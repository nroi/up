package controllers

import utils.Implicits._
import java.io.FileOutputStream
import java.math.BigInteger
import java.io.File
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, PosixFileAttributes}
import java.nio.file.{Files, LinkOption, Path}
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject._

import org.apache.commons.codec.digest.{DigestUtils, HmacUtils}
import play.api._
import play.api.mvc._

import scala.collection.JavaConversions._
import play.api.Configuration
import javax.inject.Inject


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(configuration: Configuration) extends Controller {

  sealed trait AuthFailure {
    val reason: String
  }
  object AuthStatus {
    object InvalidHmac extends AuthFailure {
      val reason: String = "Invalid HMAC"
    }
    object MissingHmac extends AuthFailure {
      val reason: String = "No HMAC provided"
    }
    object MissingToken extends AuthFailure {
      val reason: String = "No token provided"
    }
  }

  var presentations: Set[String] = {
    Option(Definitions.PresentationDir.listFiles()) match {
      case None =>
        throw new IllegalStateException(s"Expected directory ${Definitions.PresentationDir} to exist.")
      case Some(files) => files.map(_.getName).toSet
    }
  }

  val tokenQueue = new ConcurrentLinkedQueue[String]()

  private val random = new SecureRandom()

  def generatePresentationName(digest: String, filename: String): String = {
    @annotation.tailrec
    def tryDigest(fullDigest: String, fileExtension: String, digestLength: Int): String = {
      if (digestLength > fullDigest.length) {
        throw new IllegalArgumentException(s"Illegal digest length: $digestLength")
      }
      val partialDigest = fullDigest.substring(0, digestLength)
      val basename = partialDigest + fileExtension
      if (presentations.contains(basename)) {
        val digestFromSymlink = {
          val presFile = new File(Definitions.PresentationDir, basename)
          presFile.getCanonicalFile.getName
        }
        if (digestFromSymlink == digest) {
          partialDigest
        } else {
          tryDigest(fullDigest, fileExtension, digestLength + 1)
        }
      }
      else {
        partialDigest
      }
    }

    val extension = if (filename.contains(".")) {
      val last = filename.rsplitn("\\.", 2).last
      if (last.forall(_.isLetterOrDigit)) {
        Some(last)
      }
      else {
        None
      }
    } else {
      None
    }
    val digestString = tryDigest(digest, extension.getOrElse(""), Definitions.MinDigestLength)
    extension match {
      case Some(ext) => s"$digestString.$ext"
      case None => s"$digestString"
    }
  }

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action { request =>
    val hostname: String = Definitions.hostname(configuration)
    Ok(views.html.index(hostname)(request))
  }

  def newToken() = Action(parse.multipartFormData) { request =>
    Logger.debug("Attempt to POST new token.")
    val maybeToken = request.body.dataParts.get("token").map {
      case token +: Nil => token
    }
    val maybeHmac = request.body.dataParts.get("hmac").map {
      case hmac +: Nil => hmac
    }
    val authorizationStatus: Either[AuthFailure, String] = (maybeHmac, maybeToken) match {
      case (None, _) => Left(AuthStatus.MissingHmac)
      case (Some(_), None) => Left(AuthStatus.MissingToken)
      case (Some(hmac), Some(token)) =>
        val key = scala.io.Source.fromFile(Definitions.Keyfile.getAbsolutePath).mkString.stripSuffix("\n")
        val hexString = HmacUtils.hmacSha256Hex(key, token)
        if (hexString == hmac) {
          Right(token)
        } else {
          Left(AuthStatus.InvalidHmac)
        }
    }
    authorizationStatus match {
      case Right(token) =>
        tokenQueue.add(token)
        Ok("Token accepted.\n")
      case Left(authFailure) =>
        Forbidden(authFailure.reason + ".\n")
    }
  }

  def upload() = Action(parse.multipartFormData) { request =>
    request.body.dataParts.get("token") match {
      case Some(token +: Nil) if tokenQueue contains token =>
        tokenQueue.remove(token)
        request.body.file("file").map { file =>
          val bytes = Files.readAllBytes(file.ref.file.toPath)
          val digest = DigestUtils.sha512Hex(bytes)
          val presNamePath: Path = {
            val presName = generatePresentationName(digest, file.filename)
            new File(Definitions.PresentationDir, presName).toPath
          }
          val attrs: PosixFileAttributes = Files.getFileAttributeView(
            Definitions.PresentationDir.toPath, classOf[PosixFileAttributeView]).readAttributes()
          val parentGroup: GroupPrincipal = attrs.group()
          val movedFile = new File(Definitions.HashDir, digest)
          val alreadyExists = !movedFile.createNewFile()
          if (!alreadyExists) {
            val out = new FileOutputStream(movedFile)
            out.write(bytes)
            file.ref.file.delete()
            Files.getFileAttributeView(
              movedFile.toPath, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS).setGroup(parentGroup)
          }
          val privateRequest = request.body.dataParts.get("private").exists(_.contains("on"))
          if (privateRequest) {
            Ok("File has been uploaded.")
          } else {
            if (!alreadyExists) {
              Files.createSymbolicLink(presNamePath, movedFile.toPath)
            }
            Ok(s"${Definitions.retrievalUrl(configuration)}/${presNamePath.getFileName.toString}\n")
          }
        }.getOrElse {
          UnprocessableEntity(views.html.error(UNPROCESSABLE_ENTITY, "No file provided."))
        }
      case Some(_ +: Nil) =>
        Logger.info("currently available tokens: " + tokenQueue.toList)
        Forbidden(views.html.error(FORBIDDEN, "Invalid token."))
      case None =>
        Forbidden(views.html.error(FORBIDDEN, "No token provided."))
    }
  }
}

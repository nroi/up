package controllers

import utils.Implicits._
import java.io.{File, FileOutputStream}
import java.math.BigInteger
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, PosixFileAttributes}
import java.nio.file.{Files, LinkOption, Path, StandardWatchEventKinds}
import java.security.{MessageDigest, SecureRandom}
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject._

import play.api._
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() extends Controller {

  //noinspection GetGetOrElse
  val homeDir: String = System.getenv().asScala.get("HOME").getOrElse {
    throw new IllegalStateException("$HOME directory not found.")
  }

  val hashDir = new File(homeDir, Constants.hashDirectory)

  var presentations: Set[String] = {
    val presDir = new File(homeDir, Constants.directory)
    Option(presDir.listFiles()) match {
      case None =>
        throw new IllegalStateException(s"Expected directory $presDir to exist.")
      case Some(files) => files.map(_.getName).toSet
    }
  }

  val tokenQueue = new ConcurrentLinkedQueue[String]()

  new Thread(new Runnable {
    val comDir = new File(homeDir, "communicator")
    val comFile = new File(comDir, "communicator")
    override def run(): Unit = {
      @annotation.tailrec
      def waitToken(): String = {
        val watcher = comDir.toPath.getFileSystem.newWatchService
        comDir.toPath.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
        val watchKey = watcher.take
        val events = watchKey.pollEvents()
        if (events.map(_.context).exists { case p: Path => p.endsWith("communicator") }) {
          val source = scala.io.Source.fromFile(comFile)
          val lines = try {
            source.mkString
          } finally {
            source.close()
          }
          val token = lines.trim
          if (token.nonEmpty) {
            token
          } else {
            waitToken()
          }
        }
        else {
          waitToken()
        }
      }

      while (true) {
        val token = waitToken()
        Logger.info(s"token: <$token>")
        tokenQueue.add(token)
        Logger.info(s"token added to Queue: ${tokenQueue.toList}")
        // overwrite content
        val _ = new FileOutputStream(comFile)
      }
    }
  }).start()

  private val random = new SecureRandom()

  def generateRandomName: String = {
    new BigInteger(20, random).toString(32)
  }

  def generateDigest(content: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-512")
    val hash = md.digest(content)
    String.format("%064x", new java.math.BigInteger(1, hash))
  }

  def generatePresentationName(digest: String, filename: String): String = {
    @annotation.tailrec
    def tryDigest(fullDigest: String, fileExtension: String, digestLength: Int): String = {
      if (digestLength > fullDigest.length) {
        throw new IllegalArgumentException(s"Illegal digest length: $digestLength")
      }
      val partialDigest = fullDigest.substring(0, digestLength)
      if (presentations.contains(partialDigest + fileExtension)) {
        tryDigest(fullDigest, fileExtension, digestLength + 1)
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
    val digestString = tryDigest(digest, extension.getOrElse(""), Constants.MinDigestLength)
    extension match {
      case Some(ext) => s"$digestString.$ext"
      case None => s"$digestString"
    }
  }

  val filePath = new File(homeDir, Constants.directory)
  Logger.logger.debug(s"storing files in ${filePath.toString}")

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action { request =>
    Ok(views.html.index(request))
  }

  def upload() = Action(parse.multipartFormData) { request =>
    request.body.dataParts.get("token") match {
      case Some(token +: Nil) if tokenQueue contains token =>
        tokenQueue.remove(token)
        request.body.file("file").map { file =>
          import java.io.File
          val bytes = Files.readAllBytes(file.ref.file.toPath)
          val digest = generateDigest(bytes)
          val presNamePath: Path = {
            val presName = generatePresentationName(digest, file.filename)
            new File(new File(homeDir, Constants.directory), presName).toPath
          }
          val attrs: PosixFileAttributes = Files.getFileAttributeView(
            filePath.toPath, classOf[PosixFileAttributeView]).readAttributes()
          val parentGroup: GroupPrincipal = attrs.group()
          val movedFile = new File(hashDir, digest)
          movedFile.createNewFile()
          val out = new FileOutputStream(movedFile)
          val outBytes = Files.readAllBytes(file.ref.file.toPath)
          // TODO reading the bytes directly would be better than first creating a temporary file and then reading
          // the bytes.
          out.write(outBytes)
          file.ref.file.delete()
          Files.getFileAttributeView(
            movedFile.toPath, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS).setGroup(parentGroup)
          val privateRequest = request.body.dataParts.get("private").exists(_.contains("on"))
          if (!privateRequest) {
            // note: we assume that the linked directory is available through the web,
            // while parent directory of movedFile is not.
            Files.createSymbolicLink(presNamePath, movedFile.toPath)
            Ok(s"${Constants.retrievalUrl}/${presNamePath.getFileName.toString}\n")
          }
          else {
            Ok("File has been uploaded.")
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

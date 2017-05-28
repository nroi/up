package controllers

import java.io.File

import play.api.Configuration

object Definitions {

  val HomeDir: String = Option(System.getenv("HOME")).getOrElse {
    throw new IllegalStateException("$HOME directory not found.")
  }
  val HashDir = new File(HomeDir, "hash")
  def hostname(configuration: Configuration): String = {
    configuration.getString("play.hostname") match {
      case Some(hostname) => hostname
      case None => throw new IllegalStateException("Expected hostname to be defined in application.conf")
    }
  }
  private val PresentationBasename = "f"
  def retrievalUrl(configuration: Configuration): String = s"https://${hostname(configuration)}/$PresentationBasename"
  val Keyfile = new File(HomeDir, "key")
  val MinDigestLength = 2

  // Were the symlinks are stored.
  val PresentationDir = new File(HomeDir, PresentationBasename)
}

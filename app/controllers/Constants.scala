package controllers

import java.io.File

object Constants {

  val HomeDir: String = Option(System.getenv("HOME")).getOrElse {
    throw new IllegalStateException("$HOME directory not found.")
  }
  val HashDir = new File(HomeDir, "hash")
  val Hostname = "up.helios.click"
  private val PresentationBasename = "f"
  val RetrievalUrl = s"https://$Hostname/$PresentationBasename"
  val Keyfile = new File(HomeDir, "key")
  val MinDigestLength = 2

  // Were the symlinks are stored.
  val PresentationDir = new File(HomeDir, PresentationBasename)
}

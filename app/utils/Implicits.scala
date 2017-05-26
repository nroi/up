package utils

object Implicits {
  implicit def stringToRichString(s: String): RichString = new RichString(s)
}

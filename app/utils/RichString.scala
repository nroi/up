package utils

class RichString(val inner: String) {
  def rsplitn(regex: String, n: Int): Array[String] = {
    inner.reverse.split(regex, n).map(_.reverse).reverse
  }
}

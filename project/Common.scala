

object Common {
  def organization = "com.iterable"
  def version = "4.0.0-M4"
  def playVersion = System.getProperty("play.version", "2.7.3")
  def scalaVersion = System.getProperty("scala.version", "2.13.1")
  def crossScalaVersions = Seq(scalaVersion, "2.12.10", "2.11.12")
}

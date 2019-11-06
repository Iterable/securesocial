

object Common {
  def organization = "com.iterable"
  def version = "4.0.0-M3"
  def playVersion = System.getProperty("play.version", "2.7.3")
  def scalaVersion = System.getProperty("scala.version", "2.12.8")
  def crossScalaVersions = Seq(scalaVersion, "2.11.12")
}

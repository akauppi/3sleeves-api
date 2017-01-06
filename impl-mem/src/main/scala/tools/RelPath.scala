package impl.calot.tools

/*
* Relative path, of either a subpath (ends in a slash) or a log (does not end in a slash).
*/
case class RelPath(s: String) {
  import RelPath._
  require(s.head != "/", s"Relative path expected: $s")
  require(!s.contains("//"), s"Double '//' not allowed in the path: $s")

  val stageName: String = s.split("/").head    // name of this path stage, or log

  def stageNames: Seq[String] = s.split("/")

  def isLastStage: Boolean = deeper == None

  def isLog: Boolean = !s.endsWith("/")

  //def stageIsLog: Boolean = isLastStage && isLog  // same as: !s.contains("/")

  def deeper: Option[RelPath] = s match {
    case Rdeeper(x) => Some(RelPath(x))
    case _ => None
  }
}

object RelPath {
  private
  val Rdeeper= """[^/]+/(.+)""".r
}

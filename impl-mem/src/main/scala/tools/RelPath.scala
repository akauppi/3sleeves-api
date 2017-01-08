package impl.calot.tools

import scala.annotation.tailrec

/*
* Relative path, of either a branch or a log as the final stage.
*/
sealed
abstract class RelPath(val name: String, val tail: Option[RelPath]) {
  import RelPath._
  assert(name.nonEmpty)

  def isLastStage: Boolean = tail.isEmpty

  def lastStageName: String = tail.map(_.lastStageName).getOrElse(name)
}

object RelPath {

  def apply(s: String): RelPath = {
    require(s.head != "/", s"Relative path expected: $s")
    require(!s.contains("//"), s"Double '//' not allowed in the path: $s")

    val parts: Seq[String] = s.split("/")

    if (parts.last.isEmpty) {
      val rev = parts.reverse.tail    // skip the empty entry
      genAll[BranchPath](rev, (name,tail) => new BranchPath(name,tail))
    } else {
      genAll[LogPath](parts.reverse, (name,tail) => new LogPath(name,tail))
    }
  }

  private
  def genAll[T <: RelPath](stagesRev: Seq[String], gen: (String, Option[T]) => T ): T = {
    assert(stagesRev.nonEmpty)

    val bottom: T = gen(stagesRev.head, None)   // bottom-most entry = seed

    stagesRev.tail.foldLeft(bottom) { (child: T, name: String) =>
      gen(name, child)
    }
  }

  private
  val Rdeeper= """[^/]+/(.+)""".r
}

class LogPath private[this] (name: String, tail: Option[LogPath]) extends RelPath(name,tail)
class BranchPath private[this] (name: String, tail: Option[BranchPath]) extends RelPath(name,tail)

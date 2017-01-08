package impl.calot.tools

import scala.annotation.tailrec

/*
* Relative path, of either a branch or a log as the final stage.
*/
sealed
abstract class AnyPath(val name: String, val tail: Option[AnyPath]) {
  import AnyPath._
  assert(name.nonEmpty)

  def isLastStage: Boolean = tail.isEmpty

  def last: this.type = tail.map(_.last).getOrElse(this)
}

object AnyPath {

  // Create from absolute path. The path can lead to either a branch or a log.
  //
  // Throws 'InvalidArgumentException' if something's wrong with 's'.
  //
  def fromAbs(s: String): AnyPath = {
    commonRequires(s)

    if (s.endsWith("/")) {
      BranchPath.fromAbs(s)
    } else {
      LogPath.fromAbs(s)
    }
  }

  private
  def commonRequires(s: String): Unit = {
    require(s.head == "/", s"Absolute path expected: $s")
    require(!s.contains("//"), s"Double '//' not allowed in the path: $s")
  }

  private
  def genAll[T <: AnyPath](stagesRev: Seq[String], gen: (String, Option[T]) => T ): T = {
    assert(stagesRev.nonEmpty)

    val bottom: T = gen(stagesRev.head, None)   // bottom-most entry = seed

    stagesRev.tail.foldLeft(bottom) { (child: T, name: String) =>
      gen(name, child)
    }
  }

  private
  val Rdeeper= """[^/]+/(.+)""".r

  //--- LogPath ---

  class LogPath private[this] (name: String, tail: Option[LogPath]) extends AnyPath(name,tail)

  object LogPath {
    def fromAbs(s: String): LogPath = {
      commonRequires(s)
      require(!s.endsWith("/"), s"Expecting a path to a log: $s")

      val parts: Seq[String] = s.split("/")

      genAll[LogPath](parts.reverse, (name,tail) => new LogPath(name,tail))
    }
  }

  //--- BranchPath ---

  class BranchPath private[this] (name: String, tail: Option[BranchPath]) extends AnyPath(name,tail)

  object BranchPath {
    def fromAbs(s: String): BranchPath = {
      commonRequires(s)
      require(s.endsWith("/"), s"Expecting a path to a branch: $s")

      val parts: Seq[String] = s.dropRight(1).split("/")    // i.e. parts in order, no empty entry at the end

      genAll[BranchPath](parts.reverse, (name,tail) => new BranchPath(name,tail))
    }
  }
}

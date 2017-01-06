package threeSleeves

import java.time.{Instant, Period}

import akka.stream.scaladsl.{Flow, Source}

import scala.util.Try

trait StreamsAPI {
  import StreamsAPI._

  // Create a path or a log
  //
  // Returns:
  //    Success(true) if we created the entry (and possibly entries leading to it)
  //    Success(false) if it was already there
  //    Failure(Unauthorized)
  //
  def create( path: String )(implicit uid: UID): Try[Boolean]

  // Open a stream for writing to a log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(n,seq(data))` to 'n's that got persisted in the back end
  //    Failure(Unauthorized)
  //
  // Note: The 'n' are only used in this flow, and they come from the client. They are _not_ stored in the back end,
  //    but provide the client a means to know, which data got persisted in the back end. The numbers are expected to
  //    be incrementing, and not every one of them will be returned (only the largest ones persisted).
  //
  def write( path: String )(implicit uid: UID): Try[Flow[Tuple2[Long,Seq[Array[Byte]]],Long,_]]

  // Read a stream
  //
  // Returns:
  //    Success(source) if successful
  //      source provides `(readPos,author,seq(data))`
  //    Failure(Unauthorized)
  //    Failure(NotFound)
  //
  // These offsets come from the back end and can be used for restarting a read at a certain place in the stream,
  // by providing the `at` parameter.
  //
  // at:  `ReadPos(>=0)` start at that position. If the pos is not available, give an error ('Unavailable')
  //      `ReadPos.Beginning` is the same as `ReadPos(0)`
  //      `ReadPos.EarliestAvailable` start at the earliest available offset
  //      `ReadPos.NextAvailable` start at the next available data
  //
  //      Note: If we have need for 'LastAvailable', that can be easily supported.
  //
  def read( path: String, at: ReadPos )(implicit uid: UID): Try[Source[Tuple3[ReadPos,UID,Seq[Array[Byte]]],_]]

  // Snapshot of the status of a log or path
  //
  def status( path: String )(implicit uid: UID): Status

  // Watch for new logs
  //
  // Currently existing logs and sub-paths (indicated by the trailing '/' in their name) are listed in the first batch;
  // if the path is empty, the first batch is also empty. Subsequent batches represent new logs and paths.
  //
  def watch( path: String )(implicit uid: UID): Try[Source[Seq[String],_]]

  // Seal a log or a path
  //
  // Returns:
  //    Success(true) sealed it now
  //    Success(false) was already sealed
  //    Failure(Unautorized)
  //    Failure(NotFound)
  //
  def seal( path: String )(implicit uid: UID): Try[Boolean]
}

object StreamsAPI {

  case class UID(s: String)

  /* disabled
  object UID {
    val none = UID("")    // used in creating the
  }
  */

  class Unauthorized(msg: String) extends RuntimeException(msg)
  class NotFound(msg: String) extends RuntimeException(msg)

  case class ReadPos private (v: Long) extends AnyVal

  object ReadPos {
    val Beginning = new ReadPos(0)
    //val EarliestAvailable = new ReadPos(-2L)
    val NextAvailable = new ReadPos(-1L)

    def apply(v: Long, dummy: Boolean = false): ReadPos = {
      require(v >= 0, s"'ReadPos' offsets not >= 0: $v")    // may throw 'InvalidArgumentException'
      new ReadPos(v)
    }

    // disabled. Did not actually work below (with <=). Applications might not really need it.
    //
    //implicit val ord: Ordering[ReadPos] = Ordering.by((x:ReadPos) => x.v)
  }

  sealed abstract class Status {
    val created: Tuple2[String,Instant]
    val `sealed`: Option[Tuple2[String,Instant]]
  }

  case class PathStatus(
    created: Tuple2[String,Instant],
    `sealed`: Option[Tuple2[String,Instant]],
    logs: Seq[String],
    subPaths: Seq[String]
  ) extends Status

  case class LogStatus(
    created: Tuple2[String,Instant],
    `sealed`: Option[Tuple2[String,Instant]],
    oldestAvailable: ReadPos, // or same as 'nextPos' if log is empty
    nextPos: ReadPos,
    retentionTime: Option[Period],
    retentionSpace: Option[Long]
  ) extends Status {

    assert( oldestAvailable.v <= nextPos.v )

    def isEmpty: Boolean = oldestAvailable == nextPos
  }

  sealed abstract class Access
  object Access {
    case object Create extends Access
    case object Write extends Access
    case object Read extends Access
    case object Status extends Access
    case object Watch extends Access
    case object Seal extends Access
  }
}

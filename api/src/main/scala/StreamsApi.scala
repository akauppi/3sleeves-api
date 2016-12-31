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
  def create( path: String )(implicit token: BearerToken): Try[Boolean]

  // Open a stream for writing to a log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(n,timestamp,seq(data))` to 'n's that got persisted in the back end
  //    Failure(Unauthorized)
  //
  // Note: The 'n' are only used in this flow, and they come from the client. They are _not_ stored in the back end,
  //    but provide the client a means to know, which data got persisted in the back end. The numbers are expected to
  //    be incrementing, and not every one of them will be returned (only the largest ones persisted).
  //
  def write( path: String )(implicit token: BearerToken): Try[Flow[Tuple3[Long,Instant,Seq[Array[Byte]]],Long,_]]

  // Read a stream
  //
  // Returns:
  //    Success(source) if successful
  //      source provides `(offset,seq(data))`
  //    Failure(Unauthorized)
  //    Failure(NotFound)
  //
  // These offsets come from the back end and can be used for restarting a read at a certain place in the stream,
  // by providing the `at` parameter.
  //
  // at:  >0 start at that position. If the pos is not available, give an error ('Unavailable')
  //      `Beginning`(0) starts at the beginning. If ...-"-....
  //      `EarliestAvailable` (-1) start at the earliest available offset
  //
  def read( path: String, at: Option[ReadPos] )(implicit token: BearerToken): Try[Source[Tuple3[ReadPos,Metadata,Seq[Array[Byte]]],_]]

  // Snapshot of the status of a log or path
  //
  def status( path: String )(implicit token: BearerToken): Status

  // Watch for new logs
  //
  // Currently existing logs and sub-paths (indicated by the trailing '/' in their name) are listed in the first batch;
  // if the path is empty, the first batch is also empty. Subsequent batches represent new logs and paths.
  //
  def watch( path: String )(implicit token: BearerToken): Try[Source[Seq[String],_]]

  // Seal a log or a path
  //
  // Returns:
  //    Success(true) sealed it now
  //    Success(false) was already sealed
  //    Failure(Unautorized)
  //    Failure(NotFound)
  //
  def seal( path: String )(implicit token: BearerToken): Try[Boolean]
}

object StreamsAPI {

  class BearerToken(s: String)

  class Unauthorized(msg: String) extends RuntimeException(msg)
  class NotFound(msg: String) extends RuntimeException(msg)

  case class ReadPos(v: Long) extends AnyVal
  object ReadPos {
    val Beginning = ReadPos(0)
    val EarliestAvailable = ReadPos(-1L)

    // disabled. Did not actually work below (with <=). Applications might not really need it.
    //
    //implicit val ord: Ordering[ReadPos] = Ordering.by((x:ReadPos) => x.v)
  }

  case class Metadata(originTime: Instant, author: String)

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

  sealed abstract class Scope
  object Scope {
    case object Create extends Scope
    case object Write extends Scope
    case object Read extends Scope
    case object Status extends Scope
    case object Watch extends Scope
    case object Seal extends Scope
  }
}

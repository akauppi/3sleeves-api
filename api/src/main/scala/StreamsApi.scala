package threeSleeves

import java.time.{Instant, Period}

import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future
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
  def create[Rec <: Record]( path: String, uid: UID ): Future[Try[Boolean]]

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
  def write[Rec <: Record]( path: String, uid: UID ): Future[Try[Flow[Tuple2[Long,Seq[Rec]],Long,_]]]

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
  def read[Rec <: Record]( path: String, at: ReadPos ): Future[Try[Source[Tuple3[ReadPos,UID,Seq[Rec]],_]]]

  // Snapshot of the status of a log or path
  //
  def status( path: String ): Future[Try[AnyStatus]]

  // Watch for new logs
  //
  // Currently existing logs and sub-paths (indicated by the trailing '/' in their name) are listed in the first batch;
  // if the path is empty, the first batch is also empty. Subsequent batches represent new logs and paths.
  //
  def watch( path: String ): Future[Try[Source[Seq[String],_]]]

  // Seal a log or a path
  //
  // Returns:
  //    Success(true) sealed it now
  //    Success(false) was already sealed
  //    Failure(Unautorized)
  //    Failure(NotFound)
  //
  def seal( path: String, uid: UID ): Future[Try[Boolean]]
}

object StreamsAPI {

  case class UID(s: String)

  abstract class Record
  object Record {
    class KeylessRecord(data: Array[Byte]) extends Record
    class KeyedRecord(key: String, data: Array[Byte]) extends Record
  }

  // Failures
  //
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

  abstract class AnyStatus {
    val created: Tuple2[UID,Instant]
    val `sealed`: Option[Tuple2[UID,Instant]]
  }

  case class PathStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]],
    logs: Seq[String],
    subPaths: Seq[String]
  ) extends AnyStatus

  abstract class AnyLogStatus extends AnyStatus {
    val oldestPos: ReadPos   // or same as 'nextPos' if log is empty
    val nextPos: ReadPos

    assert( oldestPos.v <= nextPos.v )

    //def isEmpty: Boolean = oldestPos == nextPos
  }

  case class KeylessLogStatus(
   created: Tuple2[UID,Instant],
   `sealed`: Option[Tuple2[UID,Instant]],
    oldestPos: ReadPos,   // or same as 'nextPos' if log is empty
    nextPos: ReadPos,
    retentionTime: Option[Period],
    retentionSpace: Option[Long]
  ) extends AnyLogStatus

  case class KeyedLogStatus(
   created: Tuple2[UID,Instant],
   `sealed`: Option[Tuple2[UID,Instant]],
    oldestPos: ReadPos,   // or same as 'nextPos' if log is empty
    nextPos: ReadPos
  ) extends AnyLogStatus

  /* disabled
  sealed abstract class Access
  object Access {
    case object Create extends Access
    case object Write extends Access
    case object Read extends Access
    case object Status extends Access
    case object Watch extends Access
    case object Seal extends Access
  }
  */
}

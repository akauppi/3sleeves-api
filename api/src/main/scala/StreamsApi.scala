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
  //
  // Note: This can be used also for creating just a path. Have 'path' end with a slash. Value of 'lt' will be ignored.
  //      (this does not seem ideal interface..)
  //
  def create( path: String, lt: LogType, uid: UID ): Future[Try[Boolean]]

  sealed trait LogType {
    case object KeylessLog extends LogType
    case object KeyedLog extends LogType
  }

  // Open a stream for writing to a keyless log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(n,seq(data))` to 'n's that got persisted in the back end
  //
  // Note: The 'Tag' is a round-trip type that does not get stored alongside the data. It's a "tag" for the caller
  //    to know, when certain data got permanently stored. It can be for example a 'Long'. It should be increasing,
  //    because not necessarily all "tags" are returned to the caller, only the latest one permanently stored.
  //
  // Note: Writing happens as a sequence of records. This allows atomicity in case of e.g. multiple writers: the
  //    records are guaranteed to be appended to the log together, with no other seeping in between them.
  //    Any such records get the same 'ReadPos' in reading, making sure they are always either all read, or none of them.
  //
  def writeKeyless[R : R => Array[Byte],Tag]( path: String, uid: UID ): Future[Try[Flow[Tuple2[Tag,Seq[R]],Tag,_]]]

  // Open a stream for writing to a keyed log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(tag,map(key,data))` to 'tag's that got persisted in the back end (see 'writeKeyless' for more
  //          information on the tag handling; it is the same)
  //
  // The values provided at once appear atomically to the consumers (i.e. either all, or none, and at the same time).
  //
  // Note: This is otherwise the same as 'writeKeyless' but the content is a key-data map.
  //
  def writeKeyed[R : R => Array[Byte],Tag]( path: String, uid: UID ): Future[Try[Flow[Tuple2[Tag,Map[String,R]],Tag,_]]]

  // Read a keyless stream
  //
  // Returns:
  //    Success(source) if successful
  //      source provides `(readPos,metadata,data)`
  //    Failure(NotFound)
  //
  // These offsets come from the back end and can be used for restarting a read at a certain place in the stream,
  // by providing the `at` parameter.
  //
  // at:  `ReadPos(>=0)` start at that position. If the position is no longer available (it has been cleared away),
  //        the stream fails with an 'Unavailable' error. If the position is not yet available, behaviour is undefined:
  //        the stream may wait, or it may provide an error.
  //      `ReadPos.NextAvailable` start at the next available data
  //
  //      tbd. Add special values ('EarliestAvailable', 'LastAvailable', 'NextAvailable') only once we have a confirmed
  //        use case for those!
  //        `ReadPos.EarliestAvailable` start at the earliest still available offset (NOT SUPPORTED)
  //        `ReadPos.LastAvailable` start at the last value, if values exist, otherwise from the next value (NOT SUPPORTED)
  //
  def readKeyless[R: Array[Byte] => R]( path: String, at: ReadPos ): Future[Try[Source[Tuple3[ReadPos,Metadata,R],_]]]

  // Read a keyed stream
  //
  // A keyed stream is read as an initial (atomic) state and then state deltas. If stating an offset where to read from,
  // the initial state prior to that is anyways provided. The normal use is probably `ReadPos.NextAvailable` to get the
  // current state and any future changes to it. Author information is only provided for the change records; to get a
  // full log of the changes (except those that may have been compacted away), start reading from the beginning.
  //
  // Returns:
  //    Success(initial,source) if successful
  //      initial provides the starting state
  //      source provides `(readPos,metadata,key,data)`
  //    Failure(NotFound)
  //
  // nb. providing the 'ReadPos' for each entry provides less - or different - value than in reading a keyless stream.
  //    There, such position is often stored so that re-reading can continue with very little or no duplicates. With
  //    keyed logs, since we can always get an initial state, storing positions is not required. There may be no use
  //    case for keeping it in the stream, but let's see.
  //
  def readKeyed[R: Array[Byte] => R]( path: String, at: ReadPos ): Future[Try[Tuple2[Map[String,R],Source[Tuple3[ReadPos,Metadata,Map[String,R]],_]]]]

  // Snapshot of the status of a log or path
  //
  // Depending on the log, the value is one of the 'AnyStatus'-derived status classes: use pattern matching to dig into
  // the extended fields.
  //
  def status( path: String ): Future[Try[AnyStatus]]

  // Watch for new logs and branches
  //
  // Returns:
  //    Success(initial,source)             if successful
  //      initial: existing set of log and sub-branch names
  //      source: of log or sub-branch names, as they are created
  //    Failure(NotFound)                   if the path does not exist
  //    Failure(InvalidArgumentException)   if 'path' is not an absolute path to a branch
  //
  def watch( path: String ): Future[Try[Tuple2[Set[String],Source[String,_]]]]

  // Seal a log or a branch
  //
  // Returns:
  //    Success(true) sealed it now
  //    Success(false) was already sealed
  //    Failure(NotFound)
  //
  // Note: Sealing a branch means there cannot be more entries created directly within it. It does not seal branches
  //    recursively, nor does it seal logs.
  //
  def seal( path: String, uid: UID ): Future[Try[Boolean]]
}

object StreamsAPI {

  case class UID(s: String)

  case class ReadPos private (v: Long) extends AnyVal

  object ReadPos {
    //val Beginning = new ReadPos(0)
    //val EarliestAvailable = new ReadPos(-2L)
    val NextAvailable = new ReadPos(-1L)

    def apply(v: Long, dummy: Boolean = false): ReadPos = {
      require(v >= 0, s"'ReadPos' offsets not >= 0: $v")    // may throw 'InvalidArgumentException'
      new ReadPos(v)
    }

    // nb. Would be nice to get ordering working for 'ReadPos', but probably not crucial. This didn't do it:
    //
    //implicit val ord: Ordering[ReadPos] = Ordering.by((x:ReadPos) => x.v)
  }

  case class Metadata(uid: UID, time: Instant)

  // Common parts for all statuses
  //
  sealed trait AnyStatus {
    val created: Tuple2[UID,Instant]
    val `sealed`: Option[Tuple2[UID,Instant]]
  }

  case class PathStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]],
    logs: Set[String],
    branches: Set[String]
  ) extends AnyStatus

  case class KeylessLogStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]],
    oldestPos: ReadPos,   // or same as 'nextPos' if log is empty
    nextPos: ReadPos,
    retentionTime: Option[Period],
    retentionSpace: Option[Long]
  ) extends AnyStatus {

    assert(oldestPos.v <= nextPos.v)
  }

  case class KeyedLogStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]]
  ) extends AnyStatus

  // Failures
  //
  class NotFound(msg: String) extends RuntimeException(msg)
}

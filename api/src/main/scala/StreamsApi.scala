package threeSleeves

import java.time.{Instant, Period}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future

/*
* The streams interface
*/
trait StreamsAPI {
  import StreamsAPI._

  type Marshaller[R] = R => Array[Byte]
  type Unmarshaller[R] = Array[Byte] => R

  // Create a branch
  //
  // Returns:
  //    Success(true) if we created the entry (and possibly entries leading to it)
  //    Success(false) if it was already there
  //    Failure(Mismatch) if some level in the path, expected to be a branch, was already claimed by a log.
  //
  // Throws:
  //    'InvalidArgumentException' if 'path' is not as expected
  //
  // path:  Absolute path for a branch (ends in a slash).
  //
  def createBranch(path: String, uid: UID): Future[Boolean]

  // Create a keyless log
  //
  // Returns:
  //    Success(true) if we created the entry (and possibly entries leading to it)
  //    Success(false) if it was already there
  //    Failure(Mismatch) if some level in the path, expected to be a branch, was already claimed by a log.
  //
  // Throws:
  //    'InvalidArgumentException' if 'path' is not as expected
  //
  // path:  Absolute path for a log (does not end in a slash).
  //
  def createKeylessLog(path: String, uid: UID): Future[Boolean]

  // Create a keyed log
  //
  // Like 'createdKeylessLog' (see above).
  //
  // Note: The type of log may be important for the back end system, at creation. Keyless ones can support retention
  //      time/space restrictions, whereas keyed ones do not. Keyed one can support compaction (removal of intermediate
  //      values that don't matter to the end state).
  //
  def createKeyedLog(path: String, uid: UID): Future[Boolean]

  // Open a stream for writing to a keyless log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(tag,seq(data))` to 'tag's that got persisted in the back end
  //
  // Note: The 'Tag' is a round-trip type that does not get stored alongside the data. It's up to the caller to choose
  //    a type they are comfortable with (can be 'Long' but can also be e.g. some custom case class, carrying an id).
  //    It should be increasing, because not necessarily all tags are passed on to the caller, only the latest one
  //    permanently stored.
  //
  // Note: Writing happens as a sequence of records. This allows atomicity in the case of e.g. multiple writers: the
  //    records are guaranteed to be appended to the log together, with no other seeping in between them.
  //    Any such records get the same 'ReadPos' in reading, making sure they are always either all read, or none of them.
  //
  def writeKeylessBatched[R: Marshaller,Tag](path: String, uid: UID): Future[Flow[Tuple2[Tag,Seq[R]],Tag,NotUsed]]

  // Variant for writing just a single entry.
  //
  final
  def writeKeyless[R: Marshaller,Tag](path: String, uid: UID): Future[Flow[Tuple2[Tag,R],Tag,NotUsed]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val fut: Future[Flow[Tuple2[Tag,Seq[R]],Tag,NotUsed]] = writeKeylessBatched[R,Tag](path,uid)

    fut.map( (batchedFlow /*: Flow[Tuple2[Tag,Seq[R]],Tag,NotUsed]*/) => {
      Flow[Tuple2[Tag,R]]
        .map/*[Tuple2[Tag,Seq[R]]]*/( (t: Tuple2[Tag,R]) => Tuple2(t._1,Seq(t._2) ) )
        .via(batchedFlow)
    })
  }

  // Open a stream for writing to a keyed log
  //
  // Returns:
  //    Success(flow) if successful
  //      flow translates `(tag,seq(key->data))` to tags that got persisted in the back end (see 'writeKeylessBatched'
  //      for more information on the tag handling; it is the same)
  //
  // The values provided appear atomically to the consumers (i.e. either all, or none, and at the same time).
  //
  // Note: This is otherwise the same as 'writeKeyless' but the content is a sequence of key-data pairs.
  //
  def writeKeyedBatched[R: Marshaller,Tag](path: String, uid: UID): Future[Flow[Tuple2[Tag,Seq[Tuple2[String,R]]],Tag,NotUsed]]

  // Variant for writing just a single key->val.
  //
  // tbd. This is essentially the same as 'writeKeyless' - we can join the two by simply providing the 'R' or 'Tuple2[String,R]'
  //    as a type parameter to a helper function.
  //
  final
  def writeKeyed[R: Marshaller,Tag](path: String, uid: UID): Future[Flow[Tuple2[Tag,Tuple2[String,R]],Tag,NotUsed]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val fut: Future[Flow[Tuple2[Tag,Seq[Tuple2[String,R]]],Tag,NotUsed]] = writeKeyedBatched[R,Tag](path,uid)

    fut.map( (batchedFlow: Flow[Tuple2[Tag,Seq[Tuple2[String,R]]],Tag,NotUsed]) => {
      Flow[Tuple2[Tag,Tuple2[String,R]]]
        .map/*[Tuple2[Tag,Seq[Tuple2[String,R]]]]*/( (t: Tuple2[Tag,Tuple2[String,R]]) => Tuple2(t._1,Seq(t._2) ) )
        .via(batchedFlow)
    })
  }

  // Read a keyless stream
  //
  // Returns:
  //    Success(source) if successful
  //      source provides `(readPos,metadata,data)`
  //    Failure(NotFound)
  //
  // The 'readPos' offsets come from the back-end and can be used for restarting a read at a certain place in the stream,
  // by providing the `at` parameter.
  //
  // at:  `ReadPos(>=0)` start at that position. If the position is no longer available (it has been cleared away),
  //        the stream fails with an 'Unavailable' error. If the position is not yet available, behaviour is undefined:
  //        the stream may wait, or it may provide an error.
  //
  //      tbd. Add special values ('EarliestAvailable', 'LastAvailable', 'NextAvailable') only once we have a confirmed
  //        use case for those!
  //        `ReadPos.EarliestAvailable` start at the earliest still available offset (NOT SUPPORTED)
  //        `ReadPos.LastAvailable` start at the last value, if values exist, otherwise from the next value (NOT SUPPORTED)
  //        `ReadPos.NextAvailable` start at the next available data (NOT SUPPORTED)
  //
  def readKeyless[R: Unmarshaller](path: String, at: ReadPos): Future[Source[Tuple3[ReadPos,Metadata,R],_]]

  // Read a keyed stream
  //
  // A keyed stream is read as an initial (atomic) state and then state deltas for upcoming values.
  //
  // Returns:
  //    Success(initial,source) if successful
  //      initial provides the current state
  //      source provides `(metadata,map(key,data))`
  //    Failure(NotFound)
  //
  // 'rewind': if 'true', the initial state is empty and any kept entries (not removed by compaction in the back-end)
  //          are replayed. If keeping full configuration history is important, configure the back-end to not do compaction
  //          and you can get an audit trail of the changes.
  //          if 'false' (default), the initial state is the state right now, and further changes are provided by the source.
  //
  // Note: We don't use 'ReadPos' offsets by purpose. The use case for keyed data does not require storing an offset
  //    at the client side and continuing from it, so there's no need for exposing the offsets.
  //
  def readKeyed[R: Unmarshaller](path: String, rewind: Boolean = false): Future[Tuple2[Map[String,Tuple2[Metadata,R]],Source[Tuple2[Metadata,Map[String,R]],NotUsed]]]

  // tbd. Variant when no metadata is needed
  final
  def readKeyedPlain[R: Unmarshaller](path: String, rewind: Boolean = false): Future[Tuple2[Map[String,R],Source[Map[String,R],NotUsed]]] = {
    ???
  }

  // Snapshot of the status of a log or branch
  //
  // Returns:
  //    Success(status)
  //    Failure(NotFound)
  //    Failure(Mismatch) if one of the path levels is a log, or the final entry is not matching requested 'T' type
  //
  def status[T <: AnyStatus](path: String): Future[T]

  // Watch for new logs and branches
  //
  // Returns:
  //    Success(initial,source)             if successful
  //      initial: set of existing sub-branch and log nodes
  //      source: of nodes, as they are created
  //    Failure(NotFound)                   if the path does not exist
  //    Failure(InvalidArgumentException)   if 'path' is not an absolute path to a branch
  //
  // Note: We regard nodes as non-deletable (they can be sealed though), so there is no indication of their deletion
  //    (which can be done in the back-end level only).
  //
  def watch(path: String): Future[Tuple2[Set[AnyNode],Source[AnyNode,NotUsed]]]

  // Seal a branch or a log
  //
  // Returns:
  //    Success(true) sealed it now
  //    Success(false) was already sealed
  //    Failure(NotFound)
  //    Failure(Mismatch) if part of the 'path' is a log, not a branch
  //
  // Note: Sealing a branch means there cannot be more entries created directly within it. It does not seal branches
  //    recursively, nor does it seal logs.
  //
  def seal(path: String, uid: UID): Future[Boolean]
}

object StreamsAPI {

  case class UID(s: String)

  object UID {
    val Root = UID("")
  }

  case class Metadata(uid: UID, time: Instant)

  // Statuses
  //
  sealed trait AnyStatus {
    val created: Tuple2[UID,Instant]
    val `sealed`: Option[Tuple2[UID,Instant]]
  }

  case class BranchStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]],
    logs: Set[String],
    branches: Set[String]
  ) extends AnyStatus

  sealed trait AnyLogStatus extends AnyStatus {
    // tbd. placeholder for fields common to keyed and keyless logs
  }

  case class KeylessLogStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]],
    oldestPos: ReadPos,   // or same as 'nextPos' if log is empty
    nextPos: ReadPos,
    retentionTime: Option[Period],
    retentionSpace: Option[Long]
  ) extends AnyLogStatus {

    assert(oldestPos.v <= nextPos.v)
  }

  case class KeyedLogStatus(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]]
  ) extends AnyLogStatus

  // Node information
  //
  abstract class AnyNode(val name: String)
  object NodeName {
    case class BranchNode(s: String) extends AnyNode(s)
    case class KeylessLogNode(s: String) extends AnyNode(s)
    case class KeyedLogNode(s: String) extends AnyNode(s)
  }

  // Failures
  //
  case class NotFound(msg: String) extends RuntimeException(msg)
  case class Mismatch(msg: String) extends RuntimeException(msg)
  case class Sealed(msg: String) extends RuntimeException(msg)

  // ReadPos
  //
  case class ReadPos private (v: Long) extends AnyVal

  object ReadPos {
    val Beginning = new ReadPos(0)
    //val NextAvailable = new ReadPos(-1L)
    //val EarliestAvailable = new ReadPos(-2L)
    //val LastAvailable = new ReadPos(-3L)

    def apply(v: Long, dummy: Boolean = false): ReadPos = {
      require(v >= 0, s"'ReadPos' offsets not >= 0: $v")    // may throw 'InvalidArgumentException'
      new ReadPos(v)
    }

    // nb. Would be nice to get ordering working for 'ReadPos', but probably not crucial. This didn't do it:
    //
    //implicit val ord: Ordering[ReadPos] = Ordering.by((x:ReadPos) => x.v)
  }
}

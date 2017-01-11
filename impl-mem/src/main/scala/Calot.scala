package impl.calot

import java.time.Instant

import akka.stream.scaladsl.{Flow, Sink, Source}
import impl.calot.tools.AnyPath.{BranchPath, LogPath}
import impl.calot.tools.AnyPath
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI._

import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/*
* Implementation that is all-memory, no persistence.
*
* Note: There's no use for such except for as "customer 0" for Three Sleeves API.
*/
class Calot extends StreamsAPI {

  private
  val root = BranchNode.root

  // Create a branch
  //
  override
  def createBranch( path: String, uid: UID ): Future[Try[Boolean]] = {
    val bp: BranchPath = BranchPath.fromAbs(path)    // may throw 'InvalidArgumentException'

    var fresh: Boolean = false

    val fut: Future[Try[BranchNode]] = root.find( bp, (s: String) => {    // create deepest stage
      fresh = true
      BranchNode(uid,Instant.now(),s)
    })

    fut.map( x => x.map(_ => fresh) )
  }

  // Create a log
  //
  override
  def createLog( path: String, keyed: Boolean, uid: UID ): Future[Try[Boolean]] = {
    val lp: LogPath = LogPath.fromAbs(path)   // may throw 'InvalidArgumentException'

    var fresh: Boolean = false

    val fut: Future[Try[LogNode]] = root.find( lp, (s: String) => {   // create deepest stage
      fresh = true
      val now = Instant.now()
      if (keyed) KeylessLogNode(uid,now,s)
      else KeyedLogNode(uid,now,s)
    } )

    fut.map( x => x.map(_ => fresh) )
  }

  override
  def write( path: String )/*(implicit uid: UID)*/: Try[Flow[Tuple2[Long,Seq[Array[Byte]]],Long,_]] = {
    //checkAccess(uid, Access.Write)

    val tryNode: Try[LogNode] = LogNode(path,Access.Write)   // may fail with 'Unauthorized' or 'NotFound'

    // Note: With 'impl-mem', we're never persisting the values, so the 'Flow' to be returned never passes the 'Long's
    //    through.

    tryNode.map( node => {
      val sink: Sink[Tuple2[Instant,Seq[Array[Byte]]]] = node.sink

      Flow.fromSinkAndSource(sink,Source.empty)
    })
  }

  override
  def read( path: String, at: ReadPos )/*(implicit uid: UID)*/: Try[Source[Tuple3[ReadPos,UID,Seq[Array[Byte]]],_]]
    val tryNode: Try[LogNode] = LogNode(path,Access.Read)   // may fail with 'Unauthorized' or 'NotFound'

    tryNode.map( node => {

      // Source from the earliest available entry (maybe this implementation does not have retention limits, so it will
      // also be "from the beginning", but this may change).
      //
      val (source: Source[ReadPos,UID,Seq[Array[Byte]]], oldest: Long, n: Long) = node.sourceAndOldestAndSize

      at match {
        case ReadPos.EarliestAvailable =>
          source
        case ReadPos.NextAvailable =>
          source.drop(n)
        //case ReadPos.Beginning =>   // handled by 'ReadPos(0)'
        case ReadPos(x) if x >= 0 =>    // condition in case we try other 'ReadPos' pre-sets, and they didn't get added here

          if (oldest > x) {
            Failure( new NotFound(s"Offset $x no longer available (first offset is $oldest)") )
          } else if (oldest == x) {
            source
          } else {
            // tbd. Could use a means here to check that the first offset getting past is actually 'x' (i.e. offsets
            //    are continuous, but we know they are).
            //
            source.dropWhile( _._1.v <= x )
          }
      }
    })
  }

  override
  def status( path: String )(implicit uid: UID): Status = {
    ???
  }

  override
  def watch( path: String )(implicit uid: UID): Try[Source[Seq[String],_]] = {
    ???
  }

  override
  def seal( path: String )(implicit uid: UID): Try[Boolean] = {
    ???
  }
}

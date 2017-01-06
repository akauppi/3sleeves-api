package impl.calot

import java.time.Instant

import akka.stream.scaladsl.{Flow, Sink, Source}
import impl.calot.tools.RelPath
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/*
* Implementation that is all-memory, no persistence. There's no use for such except for as "customer 0" for 3Sleeves API.
*/
object Calot extends StreamsAPI {

  private
  val root = PathNode.root

  // tbd. What is this supposed to do?
  //
  override
  def create( path: String, uid: UID ): Future[Try[Boolean]] = {

    // tbd. Need a parameter to know if we're creating a key or keyless log. Maybe logs should be created outside
    //    of Three Sleeves API.
    //
    var created: Boolean = false

    def create(): AnyNode = ???

    val fut: Future[Try[AnyNode]] = root.find( RelPath(path), Some(create) )

    fut.map( x => x.map(_ => created) )
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

  /*
  * Split at the last '/'.
  */
  private
  def splitPath(s: String): Tuple2[String,String] = {
    ???
  }

  private
  def skipUntil( source: Source[ReadPos,UID,Seq[Array[Byte]]], n: Long ) = {
    source.filter( _._1 >= n )
  }

}

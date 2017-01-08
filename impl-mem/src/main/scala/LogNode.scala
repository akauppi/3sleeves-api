package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import impl.calot.AnyNode.AnyNodeActor
import impl.calot.LogNode.LogNodeActor
import threeSleeves.StreamsAPI.{ReadPos, UID}
import akka.pattern.ask

import scala.util.Failure
import scala.concurrent.Future

/*
* A node presenting a log stream
*/
class LogNode[R : (R) => Array[Byte]] private (ref: ActorRef) extends AnyNode {
  import LogNodeActor.Msg._

  // Note: The 'Long' can be provided by the caller. It's an opaque field for the implementation.
  //
  def writeFlow: Future[Flow[Tuple2[Long,Rec],Long,_]] = (ref ? Write).map(_.asInstanceOf[Flow[Tuple2[Long,Rec],Long,_]])

  def readSource(at: ReadPos): Future[Source[Tuple2[ReadPos,Rec],_]] = (ref ? Read(at)).map(_.asInstanceOf[Source[Tuple2[ReadPos,Rec],_]])
}

object LogNode {
  import AnyNode.Stamp

  // Note: The 'name' parameter is simply for tracking actors, and debugging (but it may be useful)
  //
  def apply[Rec <: Record](name: String, creator: UID)(implicit as: ActorSystem): LogNode[Rec] = {
    new LogNode( as.actorOf( Props(classOf[LogNodeActor[Rec]], creator), name = name) )
  }

  // tbd. eventually we'll use anything that has marshaller/unmarshaller to/from 'Array[Byte]'. We'll probably carry
  //    metadata separately, not as part of the record.
  //
  trait Record {
    def metadata: Metadata
    def data: Array[Byte]
  }

  case class KeylessRecord(metadata: Metadata, data: Array[Byte]) extends Record
  case class KeyedRecord(metadata: Metadata, key: String, data: Array[Byte]) extends Record

  case class Metadata(originTime: Instant, author: UID)

  case class ReadPos(v: Long) {
    require(v >= 0)
  }

  case class Status(
    oldestPos: ReadPos,   // == 'nextPos' if log is empty
    nextPos: ReadPos,     // smallest unused position
    created: Stamp,
    `sealed`: Option[Stamp]
  )


  //--- Actor stuff ---

  /*
  * Base class for keyed and keyless log nodes
  *
  * References:
  *   Akka Streams 2.4 > Dynamic Stream Handling > Dynamic fan-in and fan-out with MergeHub and BroadcastHub
  *     -> http://doc.akka.io/docs/akka/2.4.16/scala/stream/stream-dynamic.html#Dynamic_fan-in_and_fan-out_with_MergeHub_and_BroadcastHub
  */
  private
  abstract class LogNodeActor[Rec <: Record](creator: UID) extends AnyNodeActor(creator) {
    import LogNodeActor._

    // entries that a new consumer will get, before informed of later values
    //
    private
    var data: List[Rec] = List.empty   // Note: in reverse order (latest values added as head; prepending is fast for immutable lists)

    // Underlying sink and source that are spread to others via 'MergeHub' and 'BroadcastHub'
    //
    private
    val (jointSink: Sink[Rec,_], jointSource: Source[Rec,_], complete: Function0[Unit]) = {

      val source1: Source[Rec,SourceQueueWithComplete[Rec]] = Source.queue[Rec](10 /*buffer size*/, OverflowStrategy.fail)

      val queue: SourceQueueWithComplete[Rec] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

      // Wrap the outgoing source with the read position
      //
      val source2: Source[Tuple2[ReadPos,Rec],_] = source1.map( x => Tuple2(data.length,x) )

      val sink: Sink[Rec,_] = Sink.foreach( x => {
        val fut: Future[QueueOfferResult] = queue.offer(x)      // offer to current subscribers (if any)

        fut.onComplete {
          case Failure(ex) =>
            println( s"PROBLEM: $ex" )    // tbd. use logger (if this happens we hope the stream also fails; it is not production code anyways)
        }

        //disabled
        // Note: another approach could be to pipe the future value to us as an actor
        //fut pipeTo this.self    // our 'ActorRef'

        data = x :: data  // for new subscribers
      } )

      (sink,source2, () => queue.complete())
    }

    private
    val rg: RunnableGraph[Sink[Rec,_]] = MergeHub.source[Rec].to(jointSink)   // using default 'perProducerBufferSize' (16)

    private
    val oldestPos = ReadPos(0)   // does not change, since we don't lose oldest entries

    private
    def nextPos = ReadPos(data.length)   // Note: reverse order does not matter here

    /*
    * Provide a new 'Flow' to write to our buffer. It's actually a 'Sink' since we never persist the values, i.e. no
    * 'ReadPos' is ever output.
    */
    private
    def write(): Flow[Tuple2[Long,Rec],Long,_] = {

      val sink: Sink[Rec,_] = rg.run()        // local sink
      val src: Source[Long,_] = Source.empty

      val flow: Flow[Rec,Long,_] = Flow.fromSinkAndSource(sink,src)

      Flow[Tuple2[Long,Rec],Long].map(_._2).via(flow)
    }

    private
    def read(at: ReadPos): Source[Tuple2[ReadPos,Rec],_] = {

      val rg: RunnableGraph[Source[Rec, NotUsed]] =
        jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

      val mySource: Source[Tuple2[ReadPos,Rec],_] = rg.run()

      if (at == ReadPos.FromNext) {
        mySource
      } else {
        Source(data.reverse)     // values stored so far
          .zipWithIndex
          .map( Function.tupled( (rec:Rec, i:Long) => Tuple2(ReadPos(i), rec) ))
          .concat(mySource)
          .filter( _._1.v >= at.v )
      }
    }

    def receive = {
      case Msg.Write =>
        sender ! write()

      case Msg.Read(at: ReadPos) =>
        sender ! read(at)

      case Msg.Status =>
        sender ! Status( oldestPos, nextPos, created, `sealed` )

      case Msg.Seal(uid: UID) =>
        val fresh = super.seal(uid)
        if (fresh) {
          // tbd. We should abort/cancel any write streams, or at least make sure if they write after a seal, those writes
          //    will fail (not crucial for our experimental / development part).

          complete()   // no more values coming from there
        }
        sender ! fresh
    }
  }

  private
  object LogNodeActor {

    // Messages
    //
    object Msg extends AnyNodeActor.Msg {
      import AnyNodeActor.Msg

      case object Write extends Msg             // -> Flow[Tuple2[Long,Record],Long,_]
      case class Read(at: ReadPos) extends Msg  // -> Source[Tuple
      // Status (derived)
      // Seal (derived)
    }

    // Failures
    //
  }
}

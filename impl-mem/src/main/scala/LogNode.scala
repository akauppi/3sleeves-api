package impl.calot

import impl.calot.tools.RelPath

import java.time.Instant

import akka.{NotUsed}
import akka.actor.{Actor}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import threeSleeves.StreamsAPI.UID

import scala.util.Failure

import scala.concurrent.Future

/*
* A node presenting a log stream
*/
class LogNode extends AnyNode {

  // tbd.
}

object LogNode {
  def apply(stage: String): LogNode = ???


  //--- Actor stuff ---

  /*
  * Base class for keyed and keyless log nodes
  *
  * References:
  *   Akka Streams 2.4 > Dynamic Stream Handling > Dynamic fan-in and fan-out with MergeHub and BroadcastHub
  *     -> http://doc.akka.io/docs/akka/2.4.16/scala/stream/stream-dynamic.html#Dynamic_fan-in_and_fan-out_with_MergeHub_and_BroadcastHub
  */
  private
  abstract class LogNodeActor[Rec <: LogNodeActor.Record](creator: UID) extends Actor {
    import LogNodeActor._

    private
    val created: Tamponné = Tamponné(creator,Instant.now())

    private
    var `sealed`: Option[Tamponné] = None

    // entries that a new consumer will get, before informed of later values
    //
    private
    var data: List[Rec] = List.empty   // Note: in reverse order (latest values added as head; prepending is fast for immutable lists)

    // Underlying sink and source that are spread to others via 'MergeHub' and 'BroadcastHub'
    //
    private
    val (jointSink: Sink[Rec,_], jointSource: Source[Rec,_], complete: Function0[Unit]) = {

      val source: Source[Rec,SourceQueueWithComplete[Rec]] = Source.queue[Rec](10 /*buffer size*/, OverflowStrategy.fail)

      val queue: SourceQueueWithComplete[Rec] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

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

      (sink,source, () => queue.complete())
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
    def write(): Flow[Tuple2[Long,Record],Long,_] = {

      val sink: Sink[Rec,_] = rg.run()        // local sink
      val src: Source[Long,_] = Source.empty

      val flow: Flow[Rec,Long,_] = Flow.fromSinkAndSource(sink,src)

      Flow[Tuple2[Long,Record],Long].map(_._2).via(flow)
    }

    private
    def read(at: ReadPos): Source[Tuple2[ReadPos,Record],_] = {

      val rg: RunnableGraph[Source[Rec, NotUsed]] =
        jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

      val mySource: Source[Rec,_] = rg.run()

      val src = Source(data.reverse)     // values stored so far
        .concat(mySource)
        .zipWithIndex
        .map( Function.tupled( (rec:Rec, i:Long) => Tuple2(ReadPos(i), rec) ))

      src
    }

    private
    def seal(uid: UID): Unit = {

      if (`sealed`.isEmpty) {   // ignore multiple seals
        `sealed` = Some(Tamponné(uid,Instant.now()))

        // tbd. We should abort/cancel any write streams, or at least make sure if they write after a seal, those writes
        //    will fail (not crucial for our experimental / development part).

        complete()   // no more values coming from there
      }
    }

    def receive = {
      case Msg.Write =>
        write()

      case Msg.Read(at: ReadPos) =>
        read(at)

      case Msg.Status =>
        sender ! Status( oldestPos, nextPos, created, `sealed` )

      case Msg.Seal(uid) =>
        seal(uid)
    }
  }

  private
  object LogNodeActor {

    // Messages
    //
    sealed trait Msg

    object Msg {
      case object Write extends Msg             // -> Flow[Tuple2[Long,Record],Long,_]
      case class Read(at: ReadPos) extends Msg  // -> Source[Tuple
      case object Status extends Msg
      case class Seal(uid: UID) extends Msg
    }

    // Failures
    //

    // Base class for derived 'Record's
    //
    class Record(metadata: Record.Metadata, data: Array[Byte])

    object Record {
      case class Metadata(originTime: Instant, author: UID)
    }

    case class ReadPos(v: Long) {
      require(v >= 0)
    }

    case class Status(
                       oldestPos: ReadPos,   // == 'nextPos' if log is empty
                       nextPos: ReadPos,     // smallest unused position
                       created: Tamponné,
                       `sealed`: Option[Tamponné]
                     )

    // https://youtu.be/-JqL2WIkaB4 :)
    //
    case class Tamponné(uid: UID, time: Instant)
  }
}

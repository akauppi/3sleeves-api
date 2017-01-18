package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.{Actor, ActorRef}

import scala.util.Try
import akka.pattern.ask
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI.{Metadata, ReadPos, UID}

import scala.util.Failure
import scala.concurrent.Future

/*
* Common features for 'KeylessLogNode' and 'KeyedLogNode'. We store both as a list of events, though in actual
* implementations they could be completely different, keyed supporting compaction and keyless retention time/space
* limits. However, for us this offers a way to keep the code neat and tidy, it's really only needed as a reference
* implementation.
*
* For 'KeylessLogNode', 'K' is 'Unit'
* For 'KeyedLogNode', 'K' is 'String'
*/
abstract class AnyLogNode[K] extends AnyNode {
  import AnyLogNode._
  import AnyLogNodeActor._

  private
  type T = Tuple2[K,Array[Byte]]

  def writeSink(uid: UID): Future[Sink[T,_]] = {
    (ref ? WriteSink).map( _.asInstanceOf[Sink[Tuple2[Metadata,T]]] ).map( sink => {

      val tmp: Sink[T,NotUsed] = Flow[T]
        .map(t => Tuple2(Metadata(uid,Instant.now()),t))
        .to(sink)

      tmp
    } )
  }

  def readNextOffsetAndSource: Future[Try[Tuple2[Long,Source[Tuple3[ReadPos,Metadata,T],NotUsed]]]] = {
    (ref ? ReadNextOffsetAndSource).map( _.asInstanceOf[Try[Tuple2[Long,Source[Tuple3[ReadPos,Metadata,T],NotUsed]]]] )
  }
}

object AnyLogNode {
  /*** disabled
  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply[T](created: Tuple2[UID,Instant], name: String)(implicit as: ActorSystem): KeyedLogNode = {
    new AnyLogNode[T]( created, as.actorOf( Props(classOf[AnyLogNodeActor[T]]), name = name) )
  }
  ***/

  /*** disabled
  class Status(
    val created: Tuple2[UID,Instant],
    val `sealed`: Option[Tuple2[UID,Instant]]
  ) extends AnyNode.Status
  ***/

  //--- Actor side ---

  /*
  * Common actor code for 'KeylessLogActor' and 'KeyedLogActor'
  */
  trait AnyLogNodeActor[K] extends AnyNodeActor { self: Actor =>
    import AnyLogNodeActor._

    private type S = Tuple3[Metadata,K,Array[Byte]]

    // Keep a collection of historic values, and separate source for informing on new ones.
    //
    private
    var data: List[S] = List.empty   // Note: latest value as head; prepending is fast for immutable lists

    // Underlying sink and source that are spread to others via 'MergeHub' and 'BroadcastHub'
    //
    private
    val (jointSink: Sink[S,_], jointSource: Source[S,_], fComplete: Function0[Unit]) = {

      val source: Source[S,SourceQueueWithComplete[S]] = Source.queue[S](10 /*buffer size*/, OverflowStrategy.fail)
      val queue: SourceQueueWithComplete[S] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

      val sink: Sink[S,_] = Sink.foreach( x => {
        val fut: Future[QueueOfferResult] = queue.offer(x)      // offer to current subscribers (if any)

        fut.onComplete {
          case Failure(ex) =>
            println( s"PROBLEM: $ex" )    // tbd. use logger (if this happens we hope the stream also fails)
        }

        //disabled
        // Note: another approach could be to pipe the future value to us as an actor
        //fut pipeTo this.self    // our 'ActorRef'

        data = x :: data  // for later times
      } )

      (sink,source, () => queue.complete())
    }

    private
    val rg: RunnableGraph[Sink[S,_]] = MergeHub.source[S].to(jointSink)   // using default 'perProducerBufferSize' (16)

    override
    def receive: Receive = PartialFunction[Any, Unit]{

      case WriteSink =>
        val tmp: Sink[S,_] = rg.run()   // local sink
        tmp

      case ReadNextOffsetAndSource =>
        val rg: RunnableGraph[Source[S,NotUsed]] =
          jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

        val tmp: Source[S,_] = rg.run()
        Tuple2(data.size, tmp)
    }.orElse( super.receive )

    // Called by 'AnyNode'
    //
    override
    def onSeal(): Unit = {
      fComplete()   // close consuming sources and writing sinks
    }
  }

  private
  object AnyLogNodeActor {

    // Messages
    //
    case object WriteSink       // -> Try[Sink[Tuple2[Metadata,T]]]
    case object ReadNextOffsetAndSource      // -> Try[Tuple2[Long,Source[Tuple3[ReadPos,Metadata,T],NotUsed]]]
    //case object Status          // -> AnyLogNode.Status
    //case class Seal(uid: UID)   // -> Boolean
  }
}

package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.{Actor, ActorRef}

import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI.{Metadata, ReadPos, UID}

import scala.util.Failure
import scala.concurrent.{ExecutionContext, Future}

/*
* Common for 'KeylessLogNode' and 'KeyedLogNode'. We store both as a list of events, though in actual implementations
* they could be completely different, keyed supporting compaction and keyless retention time/space limits. However,
* for us this offers a way to keep the code small.
*
* Note: We also don't really need marshalling, at all, since the data never leaves the JVM.
*
* For 'KeylessLogNode', 'T' can be the record type directly.
* For 'KeyedLogNode', 'T' is 'Set[Tuple2[String,ConfigValue]]' (for atomic multiple value sets)
*/
abstract class AnyLogNode extends AnyNode {
  import AnyLogNode._
  import AnyLogNodeActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  protected type Record

  private
  type MR = Tuple2[Metadata,Record]

  def writeSink(uid: UID): Future[Sink[Record,NotUsed]] = {

    (ref ? WriteSink).map( _.asInstanceOf[Sink[MR,NotUsed]] ).map( sink => {
      Flow[Record]
        .map(t => Tuple2(Metadata(uid,Instant.now()),t))
        .to(sink)
    } )
  }

  def readPrefixAndSource: Future[Tuple2[Seq[MR],Source[MR,NotUsed]]] = {
    (ref ? ReadPrefixAndSource).map( _.asInstanceOf[Tuple2[Seq[MR],Source[MR,NotUsed]]] )
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

  //--- Actor side ---

  /*
  * Common actor code for 'KeylessLogActor' and 'KeyedLogActor'
  */
  trait AnyLogNodeActor[T] extends AnyNodeActor { self: Actor =>
    import AnyLogNodeActor._

    import scala.concurrent.ExecutionContext.Implicits.global

    private type M_T = Tuple2[Metadata,T]

    // Keep a collection of historic values, and separate source for informing on new ones.
    //
    protected
    var data: List[T] = List.empty   // latest value as head; prepending is fast

    // Underlying sink and source that are spread to others via 'MergeHub' and 'BroadcastHub'
    //
    private
    val (jointSink: Sink[T,_], jointSource: Source[T,_], fComplete: Function0[Unit]) = {

      val source: Source[T,SourceQueueWithComplete[T]] = Source.queue[T](10 /*buffer size*/, OverflowStrategy.fail)
      val queue: SourceQueueWithComplete[T] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

      val sink: Sink[T,_] = Sink.foreach( x => {
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
    val rg: RunnableGraph[Sink[T,NotUsed]] = MergeHub.source[T].to(jointSink)   // using default 'perProducerBufferSize' (16)

    override
    def receive: Receive = PartialFunction[Any, Unit]{

      case WriteSink =>
        val tmp: Sink[T,NotUsed] = rg.run()   // local sink
        sender ! tmp

      case ReadPrefixAndSource =>
        val rg: RunnableGraph[Source[T,NotUsed]] =
          jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

        val tmp: Source[T,NotUsed] = rg.run()
        sender ! Tuple2(data.reverse, tmp)
    }.orElse( super.receive )   // pass on 'Status' and 'Seal' to 'AnyNodeActor'

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
    case object WriteSink         // -> Sink[Tuple2[Metadata,T]]
    case object ReadPrefixAndSource      // -> Tuple2[Seq[Tuple2[Metadata,T]],Source[Tuple2[Metadata,T],NotUsed]]
    //case object Status          // -> Status
    //case class Seal(uid: UID)   // -> Boolean
  }
}

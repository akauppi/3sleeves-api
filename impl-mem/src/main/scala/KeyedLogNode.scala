package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.Try
import akka.pattern.ask
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{KeyedLogStatus, Metadata, ReadPos, UID}

import scala.util.Failure
import scala.collection.mutable
import scala.concurrent.Future

/*
* Node for storing keyed data.
*/
class KeyedLogNode private (created: Tuple2[UID,Instant], /*val*/ ref: ActorRef) extends AnyLogNode(created) {
  import KeyedLogNode._
  import KeyedLogNodeActor.{WriteSink,ReadSource,Status,Seal}

  def writeSink(uid: UID): Future[Sink[Tuple2[String,Array[Byte]],_]] = {

    (ref ? WriteSink).map( _.asInstanceOf[Sink[Tuple3[Metadata,String,Array[Byte]]]] ).map( sink => {

      val sink2: Sink[Tuple2[String,Array[Byte]],NotUsed] = Flow[Tuple2[String,Array[Byte]]]
        .map(t => {
          val meta: Metadata = Metadata(uid,Instant.now())
          Tuple3(meta,t._1,t._2)
        })
        .to(sink)

      sink2
    } )
  }

  def readSource: Future[Try[Tuple2[Long,Source[Tuple4[ReadPos,Metadata,String,Array[Byte]],NotUsed]]]] = {

    (ref ? ReadSource).map( _.asInstanceOf[Try[Tuple2[Long,Source[Tuple4[ReadPos,Metadata,String,Array[Byte]],NotUsed]]]] )
  }

  def status: Future[KeyedLogStatus] = (ref ? Status).map(_.asInstanceOf[KeyedLogStatus])

  override
  def seal(uid: UID): Future[Try[Boolean]] = (ref ? Seal(uid)).map(_.asInstanceOf[Try[Boolean]])
}

object KeyedLogNode {

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeyedLogNode = {
    new KeyedLogNode( Tuple2(creator,createdAt), as.actorOf( Props(classOf[KeyedLogNodeActor]), name = name) )
  }

  case class Status(
    created: Tuple2[UID,Instant],
    `sealed`: Option[Tuple2[UID,Instant]]
  ) extends AnyLogNode.Status

  //--- Actor side ---

  /*
  * The actor handling a keyed data store
  *
  * - stores the data
  */
  private
  class KeyedLogNodeActor private ( created: Tuple2[UID,Instant] ) extends Actor with AnyNodeActor {
    import KeyedLogNodeActor._

    // Keep a collection of historic values, and separate source for informing on new ones.
    //
    // Using a map, we are essentially compacting the data, automatically, only keeping the latest value.
    //
    private
    val data: mutable.Map[String,Tuple2[Metadata,Array[Byte]]] = mutable.Map.empty

    private type X = Tuple3[Metadata,String,Array[Byte]]

    // Underlying sink and source that are spread to others via 'MergeHub' and 'BroadcastHub'
    //
    private
    val (jointSink: Sink[X,_], jointSource: Source[X,_], fComplete: Function0[Unit]) = {

      val source: Source[X,SourceQueueWithComplete[X]] = Source.queue[X](10 /*buffer size*/, OverflowStrategy.fail)

      val queue: SourceQueueWithComplete[X] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

      val sink: Sink[X,_] = Sink.foreach( x => {
        val fut: Future[QueueOfferResult] = queue.offer(x)      // offer to current subscribers (if any)

        fut.onComplete {
          case Failure(ex) =>
            println( s"PROBLEM: $ex" )    // tbd. use logger (if this happens we hope the stream also fails)
        }

        //disabled
        // Note: another approach could be to pipe the future value to us as an actor
        //fut pipeTo this.self    // our 'ActorRef'

        data(x._2) = Tuple2(x._1,x._3)
      } )

      (sink,source, () => queue.complete())
    }

    private
    val rg: RunnableGraph[Sink[X,_]] = MergeHub.source[X].to(jointSink)   // using default 'perProducerBufferSize' (16)

    def receive: Receive = {

      case WriteSink =>
        val tmp: Sink[X,_] = rg.run()   // local sink
        tmp

      case ReadSource =>
        val rg: RunnableGraph[Source[X,NotUsed]] =
          jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

        val tmp: Source[X,_] = rg.run()
        Tuple2(data.size, tmp)

      case KeyedLogNodeActor.Status =>
        sender ! StreamsAPI.KeyedLogStatus(created, `sealed`)

      // Sealing a log aborts any ongoing writes and closes any reads
      //
      case Seal(uid) =>
        fComplete()   // abort writes and complete reads

        super.seal(uid)
    }
  }

  private
  object KeyedLogNodeActor {

    // Messages
    //
    case object WriteSink       // -> Try[Sink[Tuple3[Metadata,String,Array[Byte]]]]
    case object ReadSource      // -> Try[Tuple2[Long,Source[Tuple4[ReadPos,Metadata,String,Array[Byte]],NotUsed]]]
    case object Status          // -> KeyedLogStatus
    case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

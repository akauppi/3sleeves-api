package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Success, Try}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Source}
import com.typesafe.config.Config
import impl.calot.AnyNode.AnyNodeActor
import impl.calot.tools.AnyPath.BranchPath
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{Metadata, UID}

import scala.util.Failure
import tools.AnyPath

import scala.concurrent.Future

/*
* Node for storing keyed data.
*/
class KeyedLogNode[R] private (created: Tuple2[UID,Instant], val ref: ActorRef) extends AnyLogNode[R](created) {
  import KeyedLogNode._

  def writeFlow[T](implicit ev: Marshaller): Flow[Tuple3[T,String,R],T,_] = ???

  def source(implicit ev: Unmarshaller): Source[Tuple2[String,R],NotUsed] = ???

  def status: Status = ???

  override
  def seal(): Future[Boolean] = ???
}

object KeyedLogNode {

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply[R](creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeyedLogNode[R] = {
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
  class KeyedLogNodeActor private ( creator: UID ) extends Actor with AnyNodeActor {
    import KeyedLogNodeActor._

    private
    var data: List[Tuple3[Metadata,String,Array[Byte]]] = Map.empty

    def receive: Receive = {
      //case Xxx =>

      // tbd. writeFlow, read, ...

      // Sealing a log aborts any ongoing writes and closes any reads
      //
      case Seal(uid) =>
        // tbd. abort any ongoing writes and close any reads

        super.seal(uid)
    }
  }

  private
  object KeyedLogNodeActor {

    // Messages
    //
    case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import impl.calot.AnyLogNode.AnyLogNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{UID}

/*
* Node for storing keyed data.
*/
class KeyedLogNode private (protected val created: Tuple2[UID,Instant], protected val ref: ActorRef) extends AnyLogNode[String] {
  import KeyedLogNode._

  type Status = StreamsAPI.KeyedLogStatus
}

object KeyedLogNode {

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeyedLogNode = {
    new KeyedLogNode( Tuple2(creator,createdAt), as.actorOf( Props(classOf[KeyedLogNodeActor]), name = name) )
  }

  //--- Actor side ---

  /*
  * The actor handling a keyed data store
  */
  private
  class KeyedLogNodeActor private (created: Tuple2[UID,Instant]) extends Actor with AnyLogNodeActor[String] {
    import StreamsAPI.KeyedLogStatus

    override
    def status: KeyedLogStatus = KeyedLogStatus(
      created = created,
      `sealed`= `sealed`
    )
  }
}

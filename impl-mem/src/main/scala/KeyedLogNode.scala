package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import impl.calot.AnyLogNode.AnyLogNodeActor
import threeSleeves.StreamsAPI
import StreamsAPI.{KeyedLogStatus, UID, Unmarshaller}

/*
* Node for storing keyed data.
*/
class KeyedLogNode[R : Unmarshaller] private (protected val created: Tuple2[UID,Instant], protected val ref: ActorRef) extends AnyLogNode {
  import KeyedLogNode._

  type Status = StreamsAPI.KeyedLogStatus
  type Record= Map[String,R]
  type NodeActor = KeyedLogNodeActor[Record]
}

object KeyedLogNode {

  /*** disabled
  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply[R : Unmarshaller](creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeyedLogNode[R] = {
    new KeyedLogNode[R]( Tuple2(creator,createdAt), as.actorOf( Props(classOf[KeyedLogNodeActor[Map[String,R]]]), name = name) )
  }
  ***/

  //--- Actor side ---

  /*
  * The actor handling a keyed data store
  */
  private
  class KeyedLogNodeActor[T] private /*(created: Tuple2[UID,Instant])*/ extends AnyLogNodeActor[T] { self: Actor =>
    import StreamsAPI.KeyedLogStatus

    override
    def status: KeyedLogStatus = KeyedLogStatus(
      created = created,
      `sealed`= `sealed`,
      compaction = false
    )
  }
}

package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import impl.calot.AnyLogNode.AnyLogNodeActor
import impl.calot.KeylessLogNode.KeylessLogNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{KeylessLogStatus, ReadPos, UID, Unmarshaller}

/*
* Node for storing keyless data.
*/
class KeylessLogNode[R : Unmarshaller] private (protected val created: Tuple2[UID,Instant] /*, protected val ref: ActorRef*/) extends AnyLogNode {
  import KeylessLogNode._

  type Status = StreamsAPI.KeylessLogStatus
  type Record = R
  type NodeActor = KeylessLogNodeActor[Record]
}

object KeylessLogNode {

  /*** disabled
  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply[R : Unmarshaller](creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeylessLogNode[R] = {
    new KeylessLogNode[R]( Tuple2(creator,createdAt), as.actorOf( Props(classOf[KeylessLogNodeActor[R]]), name = name) )
  }
  ***/

  //--- Actor side ---

  private
  abstract class KeylessLogNodeActor[T] extends AnyLogNodeActor[T] { self: Actor =>
    import StreamsAPI.KeylessLogStatus

    //disabled: created: Tuple2[UID,Instant]

    override
    def status: KeylessLogStatus = KeylessLogStatus(
      created = created,
      `sealed`= `sealed`,
      oldestPos = ReadPos(0),
      nextPos = ReadPos(data.size),
      retentionTime = None,
      retentionSpace = None
    )
  }
}

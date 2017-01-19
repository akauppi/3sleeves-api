package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import impl.calot.AnyLogNode.AnyLogNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{ReadPos, UID}

/*
* Node for storing keyless data.
*/
class KeylessLogNode private (protected val created: Tuple2[UID,Instant], protected val ref: ActorRef) extends AnyLogNode[Unit] {
  import KeylessLogNode._

  type Status = StreamsAPI.KeylessLogStatus
}

object KeylessLogNode {

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(creator: UID, createdAt: Instant, name: String)(implicit as: ActorSystem): KeylessLogNode = {
    new KeylessLogNode( Tuple2(creator,createdAt), as.actorOf( Props(classOf[KeylessLogNodeActor]), name = name) )
  }

  //--- Actor side ---

  /*
  * The actor handling a keyed data store
  */
  private
  class KeylessLogNodeActor private (created: Tuple2[UID,Instant]) extends Actor with AnyLogNodeActor[Unit] {
    import StreamsAPI.KeylessLogStatus

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

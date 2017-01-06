package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef}
import impl.calot.LogNode.LogNodeActor.Stamp
import impl.calot.tools.RelPath
import threeSleeves.StreamsAPI.UID

import scala.util.Try

/*
* Common base class for 'LogNode' and 'PathNode'.
*
* Helps the 'PathNodeActor' quite essentially, allowing it to deal with both children the same. The ground reason for
* this is to keep logs and subpaths from using the same name (akin to how one cannot have a directory and a file with
* the same name in common file systems, though for the computer there's no reason not to).
*/
abstract class AnyNode {

  //def ref: ActorRef
}

object AnyNode {

  //--- Actor stuff ---
  //
  // Common things for both 'LogNodeActor' and 'PathNodeActor'
  //
  abstract class AnyNodeActor(creator: UID) extends Actor {
    protected
    val created: Stamp = Stamp(creator,Instant.now())

    protected
    var `sealed`: Option[Stamp] = None

    private
    def seal(uid: UID): Unit = {

      if (`sealed`.isEmpty) {   // ignore multiple seals
        `sealed` = Some(Stamp(uid,Instant.now()))

        onSeal()
      }
    }

    protected
    def onSeal(): Unit
  }


}
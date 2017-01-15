package impl.calot

import java.io.Closeable
import java.time.Instant

import akka.actor.{Actor, ActorRef}
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future
import akka.pattern.ask

/*
* Common base class for '..LogNode' and 'BranchNode'.
*
* Note: There's not much common functionality (e.g. 'Status' is similar, but since each returns their own variant,
*     it's implemented in the derived classes). However, this helps 'BranchNode's to keep both logs and branches
*     in the same collection.
*
* Note: Let's not make the nodes 'Closeable'. We don't really need it, and since they are essentially actors, the
*     whole system can be closed down by terminating the 'ActorSystem'.
*
* Note: creation stamp is immutable, thus does not need to kept within the actor.
*/
abstract class AnyNode (val created: Tuple2[UID,Instant]) {
  import AnyNode._

  def seal: Future[Boolean] //disabled: = (ref ? AnyNodeActor.Seal).map(_.asInstanceOf[Boolean])
}

object AnyNode {

  // Common status fields for all nodes
  //
  abstract class Status {
    val created: Tuple2[UID,Instant]
    val `sealed`: Option[Tuple2[UID,Instant]]
  }

  //--- Actor stuff ---
  //
  // Common things for all node actors
  //
  trait AnyNodeActor { self: Actor =>
    //import AnyNodeActor._

    protected
    var `sealed`: Option[Tuple2[UID,Instant]] = None

    // Derived classes should override this, to add in their work during a seal.
    //
    protected
    def seal(uid: UID): Boolean = {   // 'true' if was sealed now
      val fresh = `sealed`.isEmpty

      if (fresh) {   // ignore multiple seals
        `sealed` = Some(Tuple2(uid,Instant.now()))
      }
      fresh
    }
  }

  /*** disabled
  object AnyNodeActor {
    // Messages common (with same return type) to Log and Path nodes
    //
    sealed abstract class Msg

    case class Seal(uid: UID) extends Msg
  }
  ***/
}

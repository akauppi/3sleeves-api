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
*/
abstract class AnyNode/*(ref: ActorRef)*/ {
  import AnyNode._

  def seal: Future[Boolean] //disabled: = (ref ? AnyNodeActor.Seal).map(_.asInstanceOf[Boolean])
}

object AnyNode {

  //--- Actor stuff ---
  //
  // Common things for all node actors
  //
  abstract class AnyNodeActor(creator: UID) extends Actor {
    //import AnyNodeActor._

    protected
    val created = Tuple2(creator,Instant.now())

    private
    var sealedVar: Option[Tuple2[UID,Instant]] = None

    protected
    def `sealed` = sealedVar    // don't expose the 'var' to derived classes

    // Derived classes should override this, to add in their work during a seal.
    //
    protected
    def seal(uid: UID): Boolean = {   // 'true' if was sealed now
      val fresh = `sealed`.isEmpty

      if (fresh) {   // ignore multiple seals
        sealedVar = Some(Tuple2(uid,Instant.now()))
      }
      fresh
    }

    /*** disabled
    // Messages the derived classes did not handle
    //
    def receive = {
      case Seal(uid) => sender ! seal(uid)
    }
    ***/
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

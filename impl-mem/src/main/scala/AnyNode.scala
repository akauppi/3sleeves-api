package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef}
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future
import akka.pattern.ask

/*
* Common base class for '..LogNode' and 'BranchNode'.
*
* Note: Let's not make the nodes 'Closeable'. We don't really need it, and since they are essentially actors, the
*     whole system can be closed down by terminating the 'ActorSystem'.
*
* Note: Each typed class derives from 'AnyNode' and their actors derive from 'AnyNodeActor'. It may be slightly
*     confusing at first, but works and lets us model the different levels of commonalities.
*/
abstract class AnyNode {
  protected val created: Tuple2[UID,Instant]
  protected val ref: ActorRef
  protected type Status <: AnyNode.Status

  def status: Future[Status] = (ref ? AnyNodeActor.Status).map(_.asInstanceOf[Status])

  def seal(uid: UID): Future[Boolean] = (ref ? AnyNodeActor.Seal(uid)).map(_.asInstanceOf[Boolean])
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
    import AnyNodeActor._

    protected
    var `sealed`: Option[Tuple2[UID,Instant]] = None

    protected
    def isSealed = `sealed`.nonEmpty

    protected
    def status: AnyNode.Status

    protected
    def onSeal(): Unit

    // Part of derived classes' 'receive'
    //
    protected
    def receive: Receive = {

      case AnyNodeActor.Status =>
        sender ! this.status

      // Sealing a log aborts any ongoing writes and closes any reads
      //
      case Seal(uid) =>
        val fresh = `sealed`.isEmpty

        if (fresh) {   // ignore multiple seals
          onSeal()
          `sealed` = Some(Tuple2(uid,Instant.now()))
        }
        fresh
    }
  }

  object AnyNodeActor {
    // Messages common to Log and Path nodes
    //
    case object Status            // -> <: AnyNode.Status
    case class Seal(uid: UID)     // -> Boolean
  }
}

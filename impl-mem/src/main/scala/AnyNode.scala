package impl.calot

import java.io.Closeable
import java.time.Instant

import akka.actor.{Actor, ActorRef}
import impl.calot.AnyNode.AnyNodeActor.Msg
import impl.calot.LogNode.LogNodeActor.Stamp
import impl.calot.tools.RelPath
import threeSleeves.StreamsAPI.{UID,Stamp,Status}

import scala.concurrent.Future
import scala.util.Try
import akka.pattern.ask

/*
* Common base class for 'LogNode' and 'PathNode'.
*
* Helps the 'PathNodeActor' quite essentially, allowing it to deal with both children the same. The ground reason for
* this is to keep logs and subpaths from using the same name (akin to how one cannot have a directory and a file with
* the same name in common file systems, though for the computer there's no reason not to).
*/
abstract class AnyNode(ref: ActorRef) extends Closeable {
  import AnyNode._
  import AnyNodeActor.Msg._

  type Status     // should derive from 'AnyNode.Status' (is there a way to model that in #Scala?)

  def status: Future[Status] = (ref ? AskStatus).map(_.asInstanceOf[Status])

  def seal: Future[Boolean] = (ref ? AskSeal).map(_.asInstanceOf[Boolean])
}

object AnyNode {

  //--- Actor stuff ---
  //
  // Common things for all node actors
  //
  abstract class AnyNodeActor(creator: UID) extends Actor {
    protected
    val created: Stamp = Stamp(creator,Instant.now())

    protected
    var `sealed`: Option[Stamp] = None

    protected
    def seal(uid: UID): Boolean = {   // 'true' if was sealed now
      val fresh = `sealed`.isEmpty

      if (fresh) {   // ignore multiple seals
        `sealed` = Some(Stamp(uid,Instant.now()))
      }
      fresh
    }
  }

  object AnyNodeActor {
    // Messages common to Log and Path nodes
    //
    /*sealed*/ abstract class Msg
    object Msg {
      case object AskStatus extends Msg
      case object AskSeal extends Msg
    }
  }
}

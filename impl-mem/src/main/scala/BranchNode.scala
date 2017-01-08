package impl.calot

import akka.actor.{ActorRef, ActorSystem, Props}

import scala.util.{Success, Try}
import akka.pattern.ask
import impl.calot.AnyNode.AnyNodeActor
import impl.calot.tools.AnyPath.BranchPath
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.UID

import scala.util.Failure
import tools.AnyPath

import scala.concurrent.Future

/*
* Node for a certain path (akin to directory).
*/
class BranchNode private (val ref: ActorRef) extends AnyNode/*(ref)*/ {
  import BranchNode._

  // Find a log or branch node
  //
  // 'gen': If the node does not exist, this is the generator for making it (and the missing levels of path).
  //
  // Note: Having 'gen' create also the intermediate branches has the benefit that we don't need to be told the
  //      'UID' identity.
  //
  // Returns:
  //  Success(node) if found or created
  //  Failure(NotFound) if not found, and not allowed to create
  //  Failure(Mismatch) if the path contains an interim stage that exists as a log, or
  //                  if we're looking for dir/log but found the other type
  //
  def find[T <: AnyNode](ap: AnyPath, gen: Option[Function1[AnyPath,T]])(implicit as: ActorSystem): Future[Try[T]] = {

    (ref ? BranchNodeActor.Find(ap, gen)).map( _.asInstanceOf[Try[T]] )
  }

  def seal: Future[Boolean] = (ref ? BranchNodeActor.Seal).map(_.asInstanceOf[Boolean])
}

object BranchNode {

  // Provides a new path root
  //
  def root(implicit as: ActorSystem): BranchNode = {
    BranchNode("/")
  }

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(name: String, initial: Map[String,AnyNode] = Map.empty)(implicit as: ActorSystem): BranchNode = {
    new BranchNode( as.actorOf( Props(classOf[BranchNodeActor], initial), name = name) )
  }

  //--- Actor side ---
  //
  // Note: Once "Akka Typed" has frozen the interface, we can merge this with the surrounding class.

  /*
  * The actor handling one path level
  *
  * - keeps a list of the children
  */
  private
  class BranchNodeActor private ( creator: UID, initial: Map[String,AnyNode] ) extends AnyNodeActor(creator) {
    import BranchNodeActor._

    private
    var children: Map[String,AnyNode] = initial

    def receive: Receive = {
      // Find 'ap' at this stage, or below us.
      //
      case Find(ap,gen) =>
        val name = ap.name

        children.get(name) match {
          case Some(node) if ap.isLastStage =>
            Success(node)

          case Some(node: BranchNode) =>
            node.ref forward Find(ap.tail.get,gen)    // dig deeper, report to original sender

          case None if gen.isEmpty =>
            Failure( StreamsAPI.NotFound(s"Not found the ${if (ap.isInstanceOf[BranchPath]) "branch" else "log"}: $ap") )

          case None =>    // create and attach
            val last: AnyNode = gen.get(ap)

            // Create the intermediate levels, if any, using the same creation stamp as for the 'last' node
            //
            val (uid, instant) = last.created     // tbd. don't have access to there..

            val tmp: Seq[String] = ???    // tbd. names of level from 'ap'..to the last-but-one (all branches, or an empty sequence if 'last' can be tied directly).

            val (node: AnyNode,_) = tmp.foldRight( Tuple2(last,ap.last.name) ){ (name: String, b: Tuple2[AnyNode,String]) => {
              val (bNode: AnyNode, bName: String) = b

              Tuple2( BranchNode(name,Map(bName -> bNode)), name )
            }}

            children += name -> node
            Success(last)
        }

      // Sealing a branch means no futher logs are allowed to be created (in this stage). Nothing more.
      //
      case Seal(uid) =>
        super.seal(uid)

    } //disabled: .orElse(super.receive)   // fallback to 'AnyNodeActor' for 'Status' and 'Seal'
  }

  private
  object BranchNodeActor {

    // Messages
    //
    case class Find(rp: AnyPath, gen: Option[Function1[AnyPath,AnyNode]])   // -> Try[AnyNode]
    case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

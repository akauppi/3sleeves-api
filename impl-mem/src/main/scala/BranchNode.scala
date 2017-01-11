package impl.calot

import java.time.Instant

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Success, Try}
import akka.pattern.ask
import impl.calot.AnyNode.AnyNodeActor
import impl.calot.tools.AnyPath.BranchPath
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{BranchStatus, UID}

import scala.util.Failure
import tools.AnyPath

import scala.concurrent.Future

/*
* Node for a certain path (akin to directory).
*/
class BranchNode private (created: Tuple2[UID,Instant], val ref: ActorRef) extends AnyNode(created) {
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
  private
  def find[T <: AnyNode](ap: AnyPath, gen: Option[Function1[String,T]])(implicit as: ActorSystem): Future[Try[T]] = {

    (ref ? BranchNodeActor.Find(ap, gen)).map( _.asInstanceOf[Try[T]] )
  }

  def find[T <: AnyNode](ap: AnyPath, gen: Function1[String,T])(implicit as: ActorSystem): Future[Try[T]] = {
    find(ap, Some(gen))
  }

  def find[T <: AnyNode](ap: AnyPath)(implicit as: ActorSystem): Future[Try[T]] = {
    find(ap, None)
  }

  def status: Future[BranchStatus] = {
    (ref ? BranchNodeActor.Status).map {
      case x: Map[String,Any] =>
        val (branches: Seq[String], logs: Seq[String]) = x.get("names").asInstanceOf[Iterable[String]].partition( _.endsWith("/") )

        BranchStatus(
          created = created,
          `sealed` = x.get("sealed").asInstanceOf[Option[Tuple2[UID,Instant]]],
          logs = logs.toSet,
          branches = branches.toSet
        )
    }
  }

  def seal: Future[Boolean] = (ref ? BranchNodeActor.Seal).map(_.asInstanceOf[Boolean])
}

object BranchNode {

  // Provides a new path root
  //
  def root(implicit as: ActorSystem): BranchNode = {
    BranchNode(UID.Root, "/")
  }

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(creator: UID, createdAt: Instant, name: String, initial: Map[String,AnyNode] = Map.empty)(implicit as: ActorSystem): BranchNode = {
    new BranchNode( Tuple2(creator,createdAt), as.actorOf( Props(classOf[BranchNodeActor], initial), name = name) )
  }

  /*** disabled
  def apply(creator: UID, name: String, initial: Map[String,AnyNode] = Map.empty)(implicit as: ActorSystem): BranchNode = {
    BranchNode( Tuple2(creator,Instant.now()), name, initial )
  }
  ***/

  //--- Actor side ---
  //
  // Note: Once "Akka Typed" has frozen the interface, we can merge this with the surrounding class.

  /*
  * The actor handling one path level
  *
  * - keeps a list of the children
  */
  private
  class BranchNodeActor private ( creator: UID, initial: Map[String,AnyNode] ) extends Actor with AnyNodeActor {
    import BranchNodeActor._

    private
    var children: Map[String,AnyNode] = initial

    def receive: Receive = {
      // Find 'ap' at this stage, or below us.
      //
      case Find(ap,gen) =>
        val name = ap.name    // name of this stage

        children.get(name) match {
          case Some(node) if ap.isLastStage =>
            Success(node)

          case Some(node: BranchNode) =>
            node.ref forward Find(ap.tail.get,gen)    // dig deeper, report to original sender

          case None if gen.isEmpty =>
            Failure( StreamsAPI.NotFound(s"Not found the ${if (ap.isInstanceOf[BranchPath]) "branch" else "log"}: $ap") )

          case None =>    // create and attach
            val endNode: AnyNode = gen.get(ap.last.name)

            // Create the intermediate levels, if any, using the same creation stamp as for the 'last' node
            //
            val created = endNode.created

            // Names of level from 'ap'..to the last-but-one (all branches, or an empty sequence if 'last' can be tied directly).
            //
            val tmp: Seq[String] = ap.tail.map(_.name).drop(1)

            val (node: AnyNode,_) = tmp.foldRight( Tuple2(endNode,ap.last.name) ){ (name: String, b: Tuple2[AnyNode,String]) => {
              val (bNode: AnyNode, bName: String) = b

              Tuple2( BranchNode(created._1, created._2, name,Map(bName -> bNode)), name )
            }}

            children += name -> node
            Success(endNode)
        }

      // Status
      //
      case Status =>
        // 'created' is kept in the 'BranchNode'; we don't know it

        val names: Iterable[String] = children.map {
          case (name,node) if node.isInstanceOf[BranchNode] => name + "/"
          case (name,_) => name
        }

        sender ! Map("sealed" -> `sealed`, "names" -> names)

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
    case class Find(rp: AnyPath, gen: Option[Function1[String,AnyNode]])   // -> Try[AnyNode]
    case object Status          // -> TupleN[...]
    case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

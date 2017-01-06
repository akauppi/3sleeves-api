package impl.calot

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Failure, Success, Try}
import akka.pattern.ask
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI.UID

import scala.util.Failure
import tools.RelPath

import scala.concurrent.Future

/*
* Node for a certain path (akin to directory).
*/
class PathNode private (private val ref: ActorRef) extends AnyNode {   // 'ref' points to a 'PathNodeActor'
  import PathNode._

  // Find a sub-node
  //
  // If some level to the node does not exist, 'create' is called to create the missing bits.
  //
  // Returns:
  //  Success(node) if found or created
  //  Failure(NotFound) if not found, and not allowed to create
  //  Failure(Mismatch) if the path contains an interim stage that exists as a log
  //  Failure(LeafMismatch) if we're looking for dir/log but found the other type
  //
  def find(rp: RelPath, create: Option[Function0[AnyNode]])(implicit as: ActorSystem): Future[Try[AnyNode]] = {

    (ref ? PathNodeActor.Find(rp, create)).map( _.asInstanceOf[Try[AnyNode]] )
  }
}

object PathNode {

  // Provides a new path root
  //
  def root(implicit as: ActorSystem): PathNode = {
    PathNode("/")
  }

  // Note: The 'name' parameter is simply for tracking actors, and debugging (but it may be useful)
  //
  private
  def apply(name: String, initial: Map[String,AnyNode] = Map.empty)(implicit as: ActorSystem): PathNode = {
    new PathNode( as.actorOf( Props(classOf[PathNodeActor], initial), name = name) )
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
  class PathNodeActor private ( creator: UID, initial: Map[String,AnyNode] ) extends AnyNodeActor(creator) {
    import PathNodeActor._

    private
    var children: Map[String,AnyNode] = initial

    /*
    * Create a new node, and any interim paths between us and it. Return the deepest node.
    */
    private
    def create(rp: RelPath, cf: Function0[AnyNode]): AnyNode = {

      val stages: Vector[String] = rp.stageNames.toVector

      val deepest: AnyNode = cf()
      var last: AnyNode = deepest

      for( n <- stages.length-1 to 1 by -1 ) {
        last = PathNode( stages(n-1), Map(stages(n) -> last) )
      }
      children += stages.head -> last

      deepest
    }

    /*
    * tbd. Should disallow creating new streams
    */
    override
    def onSeal(): Unit = ???

    def receive = {
      // Find 'rp' at this stage, or below us.
      //
      case Find(rp,cf) =>
        val stageName = rp.stageName

        children.get(stageName) match {
          case Some(found) =>
            rp.deeper match {
              case None =>
                Success(found)   // was already there
              case Some(rp2) =>
                assert(found.isInstanceOf[PathNode])
                found.asInstanceOf[PathNode].ref forward Find(rp2,cf)    // dig deeper, report to original sender
            }

          case None if cf.isEmpty =>
            Failure(NotFound(rp))

          case None =>
            val last: AnyNode = create(rp,cf.get)
            Success(last)
        }
    }
  }

  private
  object PathNodeActor {

    // Messages
    //
    case class Find(rp: RelPath, create: Option[Function0[AnyNode]])   // -> Try[AnyNode]

    // Failures
    //
    case class NotFound(rp: RelPath) extends RuntimeException(s"Not found: $rp")
    case class Mismatch(msg: String) extends RuntimeException(msg)
    case class LeafMismatch(msg: String) extends RuntimeException(msg)
  }
}

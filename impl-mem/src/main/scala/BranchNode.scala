package impl.calot

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Success, Try}
import akka.pattern.ask
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{BranchStatus, UID}

import scala.util.Failure

import scala.concurrent.Future

/*
* Node for a certain path (akin to directory).
*/
class BranchNode private (created: Tuple2[UID,Instant], val ref: ActorRef) extends AnyNode(created) {
  import BranchNode._

  // Find a branch node
  //
  // 'gen': If the node does not exist, this is the generator for making it.
  //
  // Returns:
  //  Success(node) if found or created
  //  Failure(NotFound) if not found, and not allowed to create ('gen' == None)
  //  Failure(Mismatch) if the path contains a stage that exists as a log
  //
  def findBranch(names: Seq[String], gen: Option[(String) => BranchNode])(implicit as: ActorSystem): Future[Try[BranchNode]] = {

    (ref ? BranchNodeActor.FindAnyNode(names, gen)).map{
      case Success(node: BranchNode) => Success(node)
      case Success(x) => Failure( StreamsAPI.Mismatch(s"Expected 'BranchNode', got '${x.getClass}'") )
      case Failure(x) => Failure(x)   // note: needed like this - changes the 'Try' parameter
    }
  }

  // Find a log node (keyless of keyed)
  //
  // 'gen': If the node does not exist, this is the generator for making it.
  //
  // Returns:
  //  Success(node) if found or created
  //  Failure(NotFound) if not found, and not allowed to create ('gen' == None)
  //  Failure(Mismatch) if the path contains a stage that exists as a log, or if the final stage is not of expected type
  //
  def findLog[R, T <: AnyLogNode[R]](names: Seq[String], gen: Option[(String) => T])(implicit as: ActorSystem): Future[Try[T]] = {

    (ref ? BranchNodeActor.FindAnyNode(names, gen)).map{
      case Success(node: T) => Success(node)
      case Success(x) => Failure( StreamsAPI.Mismatch(s"Expected '${classOf[T]}', got '${x.getClass}'") )
      case Failure(x) => Failure(x)   // note: needed like this - changes the 'Try' parameter
    }
  }

  /*** disabled
  def find[R, T <: AnyLogNode[R]](lp: LogPath, gen: Function1[String,T])(implicit as: ActorSystem): Future[Try[T]] = {
    find(lp, Some(gen))
  }

  def find[R, T <: AnyLogNode[R]](lp: LogPath)(implicit as: ActorSystem): Future[Try[T]] = {
    find(lp, None)
  }
  ***/

  // Get the status of the branch
  //
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

  def seal: Future[Boolean] = {
    (ref ? BranchNodeActor.Seal).map(_.asInstanceOf[Boolean])
  }
}

object BranchNode {

  // Provides a new path root
  //
  def root(implicit as: ActorSystem): BranchNode = {
    BranchNode(UID.Root, Instant.now(), "/")
  }

  // Note: The 'name' parameter is simply for tracking actors
  //
  //private
  def apply(creator: UID, createdAt: Instant, name: String, initial: Map[String,AnyNode] = Map.empty)(implicit as: ActorSystem): BranchNode = {
    new BranchNode( Tuple2(creator,createdAt), as.actorOf( Props(classOf[BranchNodeActor], initial), name = name) )
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
  class BranchNodeActor private ( created: Tuple2[UID,Instant], initial: Map[String,AnyNode] ) extends Actor with AnyNodeActor {
    import BranchNodeActor._

    private
    var children: Map[String,AnyNode] = initial

    def receive: Receive = {
      // Find a subbranch, or log node, or create one
      //
      case FindAnyNode(names,gen) =>
        val name = names.head   // name of next stage

        var forwarded = false

        val res: Try[AnyNode] = children.get(name) match {
          case Some(node: AnyNode) if names.tail.isEmpty =>
            Success(node)

          case Some(node: BranchNode) =>
            node.ref forward FindAnyNode(names.tail,gen)    // dig deeper, report to original sender
            forwarded = true
            Success(null)

          case Some(_) =>
            Failure( StreamsAPI.Mismatch(s"A log exists where branch was expected: $name") )

          case None if gen.isEmpty =>
            Failure( StreamsAPI.NotFound(s"No such ${if (names.tail.nonEmpty) "branch" else "log"} and not going to create one: $name") )

          case None =>    // create and attach
            val seed: AnyNode = gen.get.apply(names.last)
            extend(names,seed)
            Success(seed)
        }

        if (!forwarded) {
          sender ! res
        }

      // Status
      //
      case Status =>
        val names: Iterable[String] = children.map {
          case (name,_: BranchNode) => name + "/"
          case (name,_) => name
        }

        val (b,a) = children.partition(_._2.isInstanceOf[BranchNode])

        sender ! StreamsAPI.BranchStatus(
          created = created,
          `sealed` = `sealed`,
          logs = a.keys.toSet,
          branches = b.keys.toSet
        )

      // Sealing a branch means no futher logs are allowed to be created (in this stage). Nothing more.
      //
      case Seal(uid) =>
        super.seal(uid)

    }

    /*
    * Extend the branch under 'names'. The deepest node is given as 'seed'.
    */
    private
    def extend(names: Seq[String], seed: AnyNode): Unit = {
      val seedName = names.last
      val tmp: Seq[String] = names.reverse.drop(1)

      val (creator: UID, createdAt: Instant) = seed.created

      val (first: AnyNode,firstName) = tmp.foldRight( Tuple2(seed,seedName) ){ (aName: String, b: Tuple2[AnyNode,String]) => {
        val (bNode: AnyNode, bName: String) = b

        Tuple2( BranchNode(creator, createdAt, aName, Map(bName -> bNode)), aName )
      }}

      children += firstName -> first
    }
  }

  private
  object BranchNodeActor {

    // Messages
    //
    case class FindAnyNode(names: Seq[String], gen: Option[Function1[String,AnyNode]])         // -> Try[AnyNode]
    case object Status          // -> BranchStatus
    case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

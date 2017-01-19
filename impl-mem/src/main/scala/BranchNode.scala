package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Success, Try}
import akka.pattern.ask
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{BroadcastHub, Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import impl.calot.AnyNode.AnyNodeActor
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI.{BranchStatus, UID}

import scala.util.Failure
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{TypeTag, typeOf}

/*
* Node for a certain path (akin to directory).
*/
class BranchNode private (protected val created: Tuple2[UID,Instant], protected val ref: ActorRef) extends AnyNode {
  import BranchNode._
  import BranchNodeActor._
  import scala.concurrent.ExecutionContext.Implicits.global

  override
  type Status = StreamsAPI.BranchStatus

  private
  implicit val askTimeout: Timeout = 1 seconds   // tbd. from config or some global for all ask patterns

  // Find a branch node
  //
  // 'gen': If the node does not exist, this is the generator for making it.
  //
  // Returns:
  //  Success(node) if found or created
  //  Failure(NotFound) if not found, and not allowed to create ('gen' == None)
  //  Failure(Mismatch) if the path contains a stage that exists as a log
  //
  private
  def findBranch(parts: Seq[String], gen: Option[(String) => BranchNode]): Future[Try[BranchNode]] = {

    if (parts.isEmpty) {
      Future.successful( Success(this) )
    } else {
      (ref ? BranchNodeActor.FindAnyNode(parts, gen)).map{
        case Success(node: BranchNode) => Success(node)
        case Success(x) => Failure( StreamsAPI.Mismatch(s"Expected 'BranchNode', got '${x.getClass}'") )
        case Failure(x) => Failure(x)   // note: needed like this - changes the 'Try' parameter
      }
    }
  }

  def findBranch(parts: Seq[String], gen: (String) => BranchNode): Future[Try[BranchNode]] = {
    findBranch(parts, Some(gen))
  }

  def findBranch(parts: Seq[String]): Future[Try[BranchNode]] = {
    findBranch(parts, None)
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
  private
  def findLog[T <: AnyLogNode[_] : ClassTag : TypeTag](parts: Seq[String], gen: Option[(String) => T]): Future[Try[T]] = {
    require(parts.nonEmpty)

    (ref ? BranchNodeActor.FindAnyNode(parts, gen)).map{
      case Success(node: T) => Success(node)
      case Success(x) => Failure( StreamsAPI.Mismatch(s"Expected '${typeOf[T]}', got '${x.getClass}'") )
      case Failure(x) => Failure(x)   // note: needed like this - changes the 'Try' parameter
    }
  }

  def findLog[T <: AnyLogNode[_] : ClassTag : TypeTag](parts: Seq[String], gen: (String) => T): Future[Try[T]] = {
    findLog(parts, Some(gen))
  }

  def findLog[T <: AnyLogNode[_] : ClassTag : TypeTag](parts: Seq[String]): Future[Try[T]] = {
    findLog(parts, None)
  }

  // tbd. Is there a way in Akka Streams that we can just return a 'Source' right away?
  //
  def watch: Future[Source[String,NotUsed]] = {
    (ref ? Watch).map( _.asInstanceOf[Source[String,NotUsed]] )
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

    // Underlying source for new entries
    //
    private
    val (jointSource: Source[String,_], queue: SourceQueueWithComplete[String]) = {

      val source: Source[String,SourceQueueWithComplete[String]] = Source.queue[String](10 /*buffer size*/, OverflowStrategy.fail)
      val queue: SourceQueueWithComplete[String] = source.toMat(Sink.ignore)(Keep.left).run()    // tbd. is 'Sink.ignore' okay?

      (source,queue)
    }

    override
    def receive: Receive = PartialFunction[Any,Unit] {
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

          case None if !isSealed =>    // create and attach
            val seed: AnyNode = gen.get.apply(names.last)
            children += extend(names,seed)(context.system)
            queue.offer(names.head)   // entry to the watch source
            Success(seed)

          case None =>
            Failure( StreamsAPI.Sealed(s"Cannot create an entry in sealed branch: $name") )
        }

        if (!forwarded) {
          sender ! res
        }

      case Watch =>
        val rg: RunnableGraph[Source[String,NotUsed]] = jointSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

        val tmp: Source[String,NotUsed] = rg.run()
        sender ! tmp

    } orElse super.receive

    // Called by 'AnyNode'
    //
    override
    def status: BranchStatus = {
      val names: Iterable[String] = children.map {
        case (name,_: BranchNode) => name + "/"
        case (name,_) => name
      }

      val (b,a) = children.partition(_._2.isInstanceOf[BranchNode])

      StreamsAPI.BranchStatus(
        created = created,
        `sealed` = `sealed`,
        logs = a.keys.toSet,
        branches = b.keys.toSet
      )
    }

    // Called by 'AnyNode'
    //
    override
    def onSeal(): Unit = {
      queue.complete()    // close the watch streams
    }
  }

  private
  object BranchNodeActor {

    /*
    * Extend the branch under 'names'. The deepest node is given as 'seed'.
    */
    private
    def extend(names: Seq[String], seed: AnyNode)(implicit as: ActorSystem): Tuple2[String,AnyNode] = {
      val seedName = names.last
      val tmp: Seq[String] = names.reverse.drop(1)

      val (creator: UID, createdAt: Instant) = seed.created

      val (first: AnyNode,firstName) = tmp.foldRight( Tuple2(seed,seedName) ){ (aName: String, b: Tuple2[AnyNode,String]) => {
        val (bNode: AnyNode, bName: String) = b

        Tuple2( BranchNode(creator, createdAt, aName, Map(bName -> bNode)), aName )
      }}

      firstName -> first
    }

    // Messages
    //
    case class FindAnyNode(names: Seq[String], gen: Option[Function1[String,AnyNode]])    // -> Try[AnyNode]
    case object Watch             // Source[String,NotUsed]
    //case object Status          // -> BranchStatus
    //case class Seal(uid: UID)   // -> Try[Boolean]
  }
}

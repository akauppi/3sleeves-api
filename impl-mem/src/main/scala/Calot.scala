package impl.calot

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import threeSleeves.StreamsAPI
import threeSleeves.StreamsAPI._

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Try}

/*
* Implementation that is all-memory, no persistence.
*
* Note: There's no use for such except for as "customer 0" for Three Sleeves API.
*/
class Calot(implicit as: ActorSystem) extends StreamsAPI {
  import Calot._

  private
  val root = BranchNode.root

  import as.dispatcher    // ExecutionContext

  // Create a branch
  //
  override
  def createBranch( path: String, uid: UID ): Future[Try[Boolean]] = {
    val parts: Seq[String] = parseBranchPath(path)    // may throw 'InvalidArgumentException'

    var fresh: Boolean = false

    val fut: Future[Try[BranchNode]] = root.findBranch( parts, (s: String) => {    // create deepest stage
      fresh = true
      BranchNode(uid,Instant.now(),s)
    })

    fut.map(_.map(_ => fresh))
  }

  // Create a log
  //
  override
  def createKeylessLog( path: String, uid: UID ): Future[Try[Boolean]] = ???    // tbd. once keyed works

  override
  def createKeyedLog( path: String, uid: UID ): Future[Try[Boolean]] = {
    val parts: Seq[String] = parseLogPath(path)    // may throw 'InvalidArgumentException'

    var fresh: Boolean = false

    val fut: Future[Try[KeyedLogNode]] = root.findLog( parts, (s: String) => {   // create deepest stage
      fresh = true
      KeyedLogNode(uid,Instant.now(),s)
    } )

    fut.map(_.map(_ => fresh))
  }

  override
  def writeKeyless[R: Marshaller,Tag]( path: String, uid: UID ): Future[Try[Flow[Tuple2[Tag,Seq[R]],Tag,_]]] = ???

  override
  def writeKeyed[R: Marshaller,Tag]( path: String, uid: UID ): Future[Flow[Tuple2[Tag,Tuple2[String,R]],Tag,_]] = {

    val mar: R => Array[Byte] = implicitly[Marshaller[R]]

    val tmp: Future[Flow[Tuple2[Tag,Tuple2[String,R]],Tag,_]] = for( node: KeyedLogNode <- logNode[KeyedLogNode](path);
      sink: Sink[Tuple2[String,Array[Byte]],NotUsed] <- node.writeSink(uid)
    ) yield {
      ???
      /***
      val sink2: Sink[Tuple2[Tag,Tuple2[String,R]],NotUsed] = Flow[Tuple2[Tag,Tuple2[String,R]]]
        .map( (t: Tuple2[Tag,Tuple2[String,R]]) => Tuple2(t._2._1, mar(t._2._2)) )
        .to(sink)

      Flow.fromSinkAndSource(sink2,Source.empty[Tag])
        ***/
    }
    tmp
  }

  override
  def readKeyless[R: Unmarshaller]( path: String, at: ReadPos ): Future[Try[Source[Tuple3[ReadPos,Metadata,R],_]]] = ???

  override
  def readKeyed[R: Unmarshaller]( path: String, at: ReadPos ): Future[Try[Tuple2[Map[String,Tuple2[Metadata,R]],Source[Tuple3[ReadPos,Metadata,Map[String,R]],_]]]] = {

    val unmar = implicitly[Unmarshaller[R]]

    type X = Tuple3[Metadata,String,Array[Byte]]
    type ReadPos_X = Tuple2[ReadPos,X]

    // Read from the beginning. Place values < 'at' into an initial map, and provide a stream from the rest.
    //
    for( node: KeyedLogNode <- logNode[KeyedLogNode](path);
         tryNextPosAndSource: Try[Tuple2[Long,Source[ReadPos_X,NotUsed]]] <- node.readNextOffsetAndSource;
         (nextPos: Long, source: Source[ReadPos_X,NotUsed]) <- tryNextPosAndSource
    ) yield {

      val border: Long = at match {
        case ReadPos.NextAvailable =>
          nextPos
        case ReadPos(x) if x >= 0 => // condition in case we try other 'ReadPos' pre-sets, and they didn't get added here
          x
      }

      val (prefix: Seq[ReadPos_X], tailSource: Source[ReadPos_X,NotUsed]) = source.prefixAndTail(border.toInt)

      val init: Map[String,Tuple2[Metadata,R]] = {
        prefix.map(_._2).map( Function.tupled( (meta:Metadata, key: String, v: Array[Byte]) => {
          Tuple2( key, Tuple2(meta,unmar(v)) )
        })).toMap
      }

      Tuple2(init,tailSource)
    }
  }

  override
  def status( path: String ): Future[Try[AnyStatus]] = {
    for( node <- anyNode(path);
       res <- node.status ) yield {
      res
    }
  }

  override
  def watch( path: String ): Future[Try[Tuple2[Set[String],Source[String,_]]]] = {
    for( node <- branchNode(path) ) yield {
      node.watch
    }
  }

  override
  def seal( path: String, uid: UID ): Future[Try[Boolean]] = {
    for( node <- anyNode(path) ) yield {
      node.seal(uid)
    }
  }

  //--- Helpers ---

  private
  def anyNode(path: String): Future[AnyNode] = {
    if (path.endsWith("/")) {
      branchNode(path)
    } else {
      logNode(path)
    }
  }

  private
  def branchNode(path: String): Future[BranchNode] = {
    root.findBranch(parseBranchPath(path))
  }

  private
  def logNode[T <: AnyLogNode[_]](path: String): Future[T] = {
    root.findLog[T](parseLogPath(path))
  }
}

object Calot {
  private
  def commonRequires(s: String): Unit = {
    require( s.startsWith("/"), s"Expected path to start with '/': $s")
    require( !s.contains("//"), s"Expected no dual '//' in path'/': $s")
  }

  private
  def parseBranchPath(s: String): Seq[String] = {
    commonRequires(s)
    require( s.endsWith("/"), s"Expected branch path to end with '/': $s")

    val tmp: Seq[String] = s.tail.dropRight(1).split("/")   // Note: Just "/" is okay - means the root node
    tmp
  }

  private
  def parseLogPath(s: String): Seq[String] = {
    commonRequires(s)
    require( !s.endsWith("/"), s"Expected log path not to end with '/': $s")

    val tmp: Seq[String] = s.tail.split("/")
    tmp
  }
}

package impl.calot

import java.time.Instant

import akka.NotUsed
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
class Calot extends StreamsAPI {
  import Calot._

  private
  val root = BranchNode.root

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
  def writeKeyed[R: Marshaller,Tag]( path: String, uid: UID ): Future[Try[Flow[Tuple2[Tag,Map[String,R]],Tag,_]]] = {

    val mar: R => Array[Byte] = implicitly[Marshaller[R]]

    for( node <- logNode(path);
      sink: Sink[Tuple2[String,Array[Byte]],_] <- node.writeSink(uid)
    ) yield {
      val sink2: Sink[Tuple3[Tag,String,R],NotUsed] = Flow[Tuple3[Tag,String,R]]
        .map( (t: Tuple3[Tag,String,R]) => Tuple2(t._2, mar(t._3)) )
        .to(sink)

      Flow.fromSinkAndSource(sink2,Source.empty)
    }
  }

  override
  def readKeyless[R: Unmarshaller]( path: String, at: ReadPos ): Future[Try[Source[Tuple3[ReadPos,Metadata,R],_]]] = ???

  override
  def readKeyed[R: Unmarshaller]( path: String, at: ReadPos ): Future[Try[Tuple2[Map[String,R],Source[Tuple3[ReadPos,Metadata,Map[String,R]],_]]]] = {

    val unmar = implicitly[Unmarshaller[R]]

    // For keyed stream, we always read from beginning. Then place values < 'at' into an initial map, and provide the stream from the rest
    //
    for( node <- logNode[KeyedLogNode](path);
         tryNexPosAndSource: Try[Tuple2[Long,Source[Tuple4[ReadPos,Metadata,String,Array[Byte]],NotUsed]]] <- node.readSource(ReadPos.Beginning);
         (nextPos,source) <- tryNexPosAndSource
    ) yield {

      val border: Long = at match {
        case ReadPos.NextAvailable =>
          nextPos
        case ReadPos(x) if x >= 0 => // condition in case we try other 'ReadPos' pre-sets, and they didn't get added here
          x
      }

      val (prefix: Seq[Tuple4[ReadPos,Metadata,String,Array[Byte]]], tailSource: Source[Tuple4[ReadPos,Metadata,String,Array[Byte]],NotUsed]) = source.prefixAndTail(border.toInt)

      val init: Map[String,R] = {
        prefix.map( Function.tupled( (_:ReadPos, _:Metadata, key: String, v: Array[Byte]) => {
          Tuple2( key, unmar(v) )
        })).toMap
      }

      Tuple2(init,tailSource)
    })
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
  def logNode[T <: AnyLogNode](path: String): Future[T] = {
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

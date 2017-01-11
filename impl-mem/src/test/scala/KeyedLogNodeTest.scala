package test

import java.time.Instant

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import impl.calot.{KeyedLogNode, LogNode}
import impl.calot.LogNode.KeylessRecord
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import threeSleeves.StreamsAPI.Record.KeyedRecord
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future
import scala.util.Success

class KeyedLogNodeTest extends FlatSpec with Matchers with ScalaFutures {
  import LogNodeTest._

  behavior of "KeyedLogNode"

  var log: KeyedLogNode[Rec] = null

  var writePassed = false

  it should "allow creation of a 'KeyedLogNode'" in {
    log = KeyedLogNode[Rec](uid, Instant.now(), "test")
  }

  it should "allow writing to the log" in {
    assume(log != null)

    val flow: Flow[Tuple3[Unit,String,Rec],Unit,_] = log.writeFlow[Unit]

    val source: Source[Tuple2[String,Rec],_] = Source(data.toList)

    val unit = ()

    val fut: Future[Done] = source
      .map( v => Tuple3(unit,v._1,v._2) )
      .via(flow)
      .runWith(Sink.ignore)

    whenReady(fut) { x =>
      x shouldBe Success(Done)
      writePassed = true
    }
  }

  it should "allow reading from the log" in {
    assume(log != null)
    assume(writePassed)

    val source: Source[Tuple2[String,Rec],NotUsed] = log.source

    val fut: Future[Seq[Tuple2[String,Rec]]] = source.take(data.length)
      .runWith(Sink.seq)

    whenReady(fut) { seq =>
      seq contains theSameElementsInOrderAs(data)
    }
  }

  it should "allow reading status for the log" in {
    assume(log != null)
    assume(writePassed)

    val st: KeyedLogNode.Status = log.status

      ...
  }

  it should "allow sealing the log" in {
    assume(log != null)

    sealPassed = true
  }

  it should "- once sealed, opened write flows should be cancelled" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    //... take that write flow
  }

  it should "- once sealed, new write flows should not be allowed to be opened" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)
  }
}

object LogNodeTest {
  val uid: UID = UID("me")

  // Log can take any type that has marshalling/unmarshalling to/from 'Array[Byte]'
  //
  case class Rec(n: Int, s: String)

  val data = Seq(
    "first" -> Rec(123,"abc"),   // tbd. more spicy samples, please!   somehow connected to the series
    "second" -> Rec(456,"def")
  )
}

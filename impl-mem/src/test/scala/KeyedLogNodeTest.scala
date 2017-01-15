package test

import java.time.Instant

import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import impl.calot.{KeyedLogNode, LogNode}
import impl.calot.LogNode.KeylessRecord
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future
import scala.util.Success

class KeyedLogNodeTest extends FlatSpec with Matchers with ScalaFutures {
  import LogNodeTest._

  behavior of "KeyedLogNode"

  var log: KeyedLogNode[Rec] = null

  var writePassed = false
  var sealPassed = false

  it should "allow creation of a 'KeyedLogNode'" in {
    log = KeyedLogNode[Rec](uid, Instant.now(), "test")
  }

  it should "allow writing to the log" in {
    assume(log != null)

    val flow: Flow[Tuple3[Unit,String,Rec],Unit,_] = log.writeFlow[Unit]

    val source: Source[Tuple2[String,Rec],_] = Source(data.toList)

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

    info(st.toString)

    st.created shouldBe 'defined
    st.created._1 shouldBe uid

    st.`sealed` shouldBe 'empty

    // ..add tests if more fields
  }

  //var writeFlowOpenedBeforeSeal = null
  //var matSourceOpenedBeforeSeal: Source[Tuple2[String,Rec],NotUsed] = null

  var matQueueToWriteFlowOpenedBeforeSeal: SourceQueueWithComplete[Tuple2[String,Rec]] = null

  var futSourceOpenedBeforeSeal: Future[Done] = null

  it should "(preparation for over-seal testing; always passes)" in {
    assume(log != null)

    val flow: Flow[Tuple3[Unit,String,Rec],Unit,_] = log.writeFlow[Unit]

    type T = Tuple2[String,Rec]

    matQueueToWriteFlowOpenedBeforeSeal = Source.queue[T](10,OverflowStrategy.fail)
      .map(t => (unit,t._1,t._2))
      .via(flow)
      .to(Sink.ignore)    // for 'Source.queue', we need to use separate 'to' and 'run' stages
      .run()

    futSourceOpenedBeforeSeal = log.source
      .runWith(Sink.ignore)    // should complete when the stream is sealed
  }

  it should "allow sealing the log" in {
    assume(log != null)

    val fut: Future[Boolean] = log.seal()

    whenReady(fut) { b =>
      assert(log.status.`sealed` == None)   // not sealed yet

      b shouldBe true   // we just sealed it

      log.status.`sealed` shouldBe 'defined
      log.status.`sealed` shouldBe 'defined

      whenReady(log.seal()) { b2 =>
        b2 shouldBe false   // was already sealed
      }

      sealPassed = true
    }
  }

  it should "- once sealed, new write flows should immediately fail when materialized" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    val flow: Flow[Tuple3[Unit,String,Rec],Unit,_] = log.writeFlow[Unit]

    val source: Source[Tuple2[String,Rec],_] = Source(data.toList)

    val fut: Future[Done] = source
      .map( v => Tuple3(unit,v._1,v._2) )
      .via(flow)
      .runWith(Sink.ignore)

    whenReady(fut.failed) { ex =>
      ex shouldBe a [OutOfMemoryError]    // tbd. replace with the right kind of error
    }
  }

  it should "- once sealed, opened write flows should be cancelled" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    val fut: Future[QueueOfferResult] = matQueueToWriteFlowOpenedBeforeSeal.offer( Tuple2("a",Rec(0,"nope")) )

    whenReady(fut) { x =>
      x shouldBe QueueOfferResult.QueueClosed
    }
  }

  it should "- once sealed, new read sources should complete" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    val source: Source[Tuple2[String,Rec],NotUsed] = log.source

    val fut: Future[Seq[Tuple2[String,Rec]]] = source   // Note: no '.take', completes only when the log is sealed
      .runWith(Sink.seq)

    whenReady(fut) { seq =>
      seq.take(data.size) contains theSameElementsInOrderAs(data)
      seq.size >= data.size   // there may be later entries
    }
  }

  it should "- once sealed, existing read sources should complete" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    whenReady(futSourceOpenedBeforeSeal) { done =>
      succeed
    }
  }
}

object LogNodeTest {
  val uid: UID = UID("me")

  val unit: Unit = ()

  // Log can take any type that has marshalling/unmarshalling to/from 'Array[Byte]'
  //
  case class Rec(n: Int, s: String)

  val data = Seq(
    "first" -> Rec(123,"abc"),   // tbd. more spicy samples, please!   somehow connected to the series
    "second" -> Rec(456,"def")
  )
}

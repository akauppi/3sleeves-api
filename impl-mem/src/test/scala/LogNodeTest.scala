package test

import akka.stream.scaladsl.{Flow, Source}
import impl.calot.LogNode
import impl.calot.LogNode.KeylessRecord
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import threeSleeves.StreamsAPI.Record.KeyedRecord
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future

class LogNodeTest extends FlatSpec with Matchers with ScalaFutures {
  import LogNodeTest._

  behavior of "LogNode"

  var log: LogNode[KeylessRecord] = null
  var log2: LogNode[KeyedRecord] = null

  var writePassed = false

  it should "allow creation of a 'LogNode' with keyless data" in {
    log = LogNode[KeylessRecord]("test", uid)
  }

  it should "allow writing to the log" in {
    assume(log != null)

    val fut: Future[Flow[Tuple2[Long,KeylessRecord],Long,_]] = log.writeFlow

    val source: Source[Tuple2[Long,KeylessRecord]] = {
      Source(1 to 10).map( _.toString.asInstanceOf[Array[Byte]] ).zipWithIndex.map( _.swap )
    }

    whenReady(fut) { writeStream =>
        ...

      writePassed = true
    }
  }

  it should "allow reading from the log" in {
    assume(log != null)
    assume(writePassed)
  }

  it should "allow reading status for the log" in {
    assume(log != null)
    assume(writePassed)
  }

  it should "allow sealing the log" in {
    assume(log != null)

    sealPassed = true
  }

  it should "once sealed, opened write flows should be cancelled" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)

    //... take that write flow
  }

  it should "once sealed, new write flows should not be allowed to be opened" in {
    assume(log != null)
    assume(writePassed)
    assume(sealPassed)
  }

  it should "allow closing, to release resources" ignore /*in*/ {
    assume(log != null)

    //log.close()   // not implemented
  }

  //--- Tests for keyed access ---

  it should "allow creation of a 'LogNode' with keyed data" in {
    node2 = LogNode[KeyedRecord]("test", uid)
  }

}

object LogNodeTest {
  val uid: UID = UID("me")
}
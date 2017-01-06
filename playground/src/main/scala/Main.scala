package playground

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future

object Main extends App {

  implicit val as = ActorSystem("playground")
  implicit val mat = ActorMaterializer()
  import as.dispatcher

  val flow: Flow[Int,Int,_] = Flow[Int].map(_+1)

  val flow1: Flow[Tuple2[Nothing,Int],Int,_] = Flow[Tuple2[Nothing,Int]].map(_._2).via(flow)

  val fut: Future[Done] = Source(1 to 10)
    .via(flow)
    .runForeach(println)

  fut.onComplete( _ =>
    as.terminate()
  )
}

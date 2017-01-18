package threeSleeves

import akka.stream.scaladsl.Source
import com.typesafe.config.{Config, ConfigValue}
import threeSleeves.StreamsAPI.UID

import scala.concurrent.Future
import scala.util.Try

/*
* Config API utilizes one or more streams, to provide a Typesafe Config -like configuration experience, but dynamically.
*/
object ConfigAPI { self: StreamsAPI =>

  // Open a config stream
  //
  // Returns:
  //    Success(source) The first batch contains the initial configuration (existing state on top of 'base').
  //                    Further entries carry changes to it.
  //    Failure(NotFound)
  //
  def open( base: Config, keyedLogPath: String ): Future[Try[Source[Map[String,ConfigValue],_]]] = {

    ???
  }
}

package threeSleeves

import akka.stream.scaladsl.{Source}
import com.typesafe.config.{Config, ConfigValue}
import threeSleeves.StreamsAPI.UID

import scala.util.Try

/*
* Config API utilizes one or more streams, to provide a Typesafe Config -like configuration experience, but dynamically.
*/
object ConfigAPI {

  // Open a config stream
  //
  // Returns:
  //    Success(config stream) The first batch contains the current configuration. Further entries carry changes to it.
  //    Failure(Unauthorized)
  //    Failure(NotFound)
  //
  def open( base: Config, keyedLogPath: String ): Try[Tuple2[Config,Source[Map[String,ConfigValue],_]]] = {

    ???
  }
}

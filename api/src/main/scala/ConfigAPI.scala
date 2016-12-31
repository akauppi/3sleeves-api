package threeSleeves

import java.time.{Instant, Period}

import akka.stream.scaladsl.{Flow, Source}
import com.typesafe.config.{Config, ConfigValue}
import threeSleeves.StreamsAPI.BearerToken

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
  def open( base: Config, path: String )(implicit token: BearerToken): Try[Source[Set[Tuple2[String,ConfigValue]],_]] = {

    ???
  }
}

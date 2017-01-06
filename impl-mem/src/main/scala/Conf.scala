package impl.calot

import com.typesafe.config.ConfigFactory

object Conf {
  private
  val c = ConfigFactory.load.getConfig("threesleeves.impl.calot")

  //...
}

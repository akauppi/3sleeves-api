package impl.plainmem

import com.typesafe.config.ConfigFactory

object Conf {
  private
  val c = ConfigFactory.load.getConfig("xxx")

  //...
}

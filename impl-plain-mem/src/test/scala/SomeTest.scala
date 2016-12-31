package test

import org.scalatest.{FlatSpec, Matchers}

class SomeTest extends FlatSpec with Matchers {

  behavior of "Something"

  it should "get here" in {
    succeed
  }
}

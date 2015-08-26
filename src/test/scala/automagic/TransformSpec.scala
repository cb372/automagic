package automagic

import org.scalatest._

object InputModels {

  case class InputCaseClass(a: Int, b: String, c: Boolean)

  class InputClass(val a: Int, val b: String, val c: Boolean)

}

object OutputModels {

  case class OutputCaseClass(a: Int, b: String, c: Boolean)

  class OutputClass(val a: Int, val b: String, val c: Boolean) {
    /* the macro should ignore this secondary constructor */
    def this(a: Int, b: String, c: Boolean, d: Any) = {
      this(a, b, c)
      throw new RuntimeException()
    }
  }

  class OutputClassWithCompanion(val a: Int, val b: String, val c: Boolean)

  object OutputClassWithCompanion {
    def apply(a: Int, b: String, c: Boolean) =
      new OutputClassWithCompanion(a, b, c)
  }

  trait OutputTrait

  case class OutputCaseClassWithFourParams(a: Int, b: String, c: Boolean, d: String)
  
  case class OutputCaseClassWithOptionalFourthParam(a: Int, b: String, c: Boolean, d: Option[String])

  case class OutputCaseClassWithDefaultFourthParam(a: Int, b: String, c: Boolean, d: String = "fallback")
}

class TransformSpec extends FlatSpec with Matchers {
  import InputModels._, OutputModels._

  it should "transform a case class to another case class" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClass](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
  }

  it should "transform a normal class to another normal class" in {
    val input = new InputClass(1, "foo", true)
    val output = transform[InputClass, OutputClass](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
  }

  it should "transform a normal class to a class with a companion object" in {
    val input = new InputClass(1, "foo", true)
    val output = transform[InputClass, OutputClassWithCompanion](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
  }

  it should "fail to compile if the output type is a trait" in {
  """
    val input = new InputClass(1, "foo", true)
    val output = transform[InputClass, OutputTrait](input)
  """ shouldNot compile
  }

  it should "use an override in preference to the input field" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClass](input, ("b", "wow"))

    output.a should be(1)
    output.b should be("wow")
    output.c should be(true)
  }

  it should "support overrides passed in either tuple syntax" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClass](input, "a" -> 5, ("b", "wow"))

    output.a should be(5)
    output.b should be("wow")
    output.c should be(true)
  }

  it should "reject overrides for unrecognised parameters" in {
  """
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClass](input, "a" -> 5, "wibble" -> "bang")
  """ shouldNot compile
  }

  it should "reject badly typed overrides" in {
    """
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClass](input, "a" -> "nope")
    """ shouldNot compile
  }

  it should "fail to compile if a parameter is missing" in {
    """
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClassWithFourFields](input, "a" -> 5)
    """ shouldNot compile
  }

  it should "set an Option parameter to None if no value can be found" in {
    pending
//    val input = InputCaseClass(1, "foo", true)
//    val output = transform[InputCaseClass, OutputCaseClassWithFourParams](input)
//
//    output.a should be(1)
//    output.b should be("wow")
//    output.c should be(true)
//    output.d should be(None)
  }

  it should "set an Option parameter to Some(value) if a value can be found" in {
    pending
//    val input = InputCaseClass(1, "foo", true)
//    val output = transform[InputCaseClass, OutputCaseClassWithOptionalFourthParam](input, "d" -> "pupi")
//
//    output.a should be(1)
//    output.b should be("wow")
//    output.c should be(true)
//    output.d should be(Some("pupi"))
  }

  it should "use a default argument if no value can be found" in {
    pending
//    val input = InputCaseClass(1, "foo", true)
//    val output = transform[InputCaseClass, OutputCaseClassWithDefaultFourthParam](input)
//
//    output.a should be(1)
//    output.b should be("wow")
//    output.c should be(true)
//    output.d should be("fallback")
  }

  it should "override the default argument if a value can be found" in {
    pending
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClassWithDefaultFourthParam](input, "d" -> "hoge")

    output.a should be(1)
    output.b should be("wow")
    output.c should be(true)
    output.d should be("hoge")
  }

}

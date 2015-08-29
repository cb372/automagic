package automagic

import org.scalatest._

object InputModels {

  case class InputCaseClass(a: Int, b: String, c: Boolean)

  class InputClass(val a: Int, val b: String, val c: Boolean)

  case class InputCaseClassWithWrongParamType(a: Int, b: Int, c: Boolean)

  class InputClassWithDef(val a: Int, val b: String) {
    def c: Boolean = true
  }

  case class TwoInts(a: Int, b: Int)

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

  class OutputClassWithDefaultFourthParam(val a: Int, val b: String, val c: Boolean, val d: String = "fallback")

  class OutputClassWithCompanionAndDefaultParam(val a: Int, val b: String, val c: Boolean, val d: String)

  object OutputClassWithCompanionAndDefaultParam {
    def apply(a: Int, b: String, c: Boolean, d: String = "fallback") =
      new OutputClassWithCompanionAndDefaultParam(a, b, c, d)
  }

  case class TwoIntsSwapped(b: Int, a: Int)

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

  it should "use zero-arg methods on the input class as if they are fields" in {
    val input = new InputClassWithDef(1, "foo")
    val output = transform[InputClassWithDef, OutputClass](input)

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
    val output = transform[InputCaseClass, OutputCaseClassWithFourParams](input, "a" -> 5)
    """ shouldNot compile
  }

  it should "fail to compile if the parameter types do not match" in {
    """
    val input = InputCaseClassWithWrongParamType(1, 234, true)
    val output = transform[InputCaseClassWithWrongParamType, OutputCaseClass](input)
    """ shouldNot compile
  }

  it should "use a default argument if no value can be found (case class)" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClassWithDefaultFourthParam](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
    output.d should be("fallback")
  }

  it should "use a default argument if no value can be found (normal class)" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputClassWithDefaultFourthParam](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
    output.d should be("fallback")
  }

  it should "use a default argument if no value can be found (companion object apply)" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputClassWithCompanionAndDefaultParam](input)

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
    output.d should be("fallback")
  }

  it should "override the default argument if a value can be found" in {
    val input = InputCaseClass(1, "foo", true)
    val output = transform[InputCaseClass, OutputCaseClassWithDefaultFourthParam](input, "d" -> "hoge")

    output.a should be(1)
    output.b should be("foo")
    output.c should be(true)
    output.d should be("hoge")
  }

  it should "not care about the order of the parameters, only the names" in {
    val input = TwoInts(1, 2)
    val output = transform[TwoInts, TwoIntsSwapped](input)

    output should be (TwoIntsSwapped(2, 1))
  }

}

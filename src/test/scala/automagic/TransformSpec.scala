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

}

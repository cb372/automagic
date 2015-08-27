import scala.language.experimental.macros

package object automagic {

  /**
   * Construct an instance of the `To` type based on the provided `from` instance and `overrides`.
   *
   * The most basic behaviour is to copy the fields from `from`, e.g.
   *
   * {{{
   *   case class Input(foo: Int, bar: String)
   *   case class Output(foo: Int, bar: String)
   *
   *   val input = Input(123, "wow")
   *   val output = transform[Input, Output](input)
   * }}}
   *
   * This will produce an `Output(123, "wow")`.
   *
   * But sometimes your input and output models don't match exactly, so you might need to add some more fields,
   * or change the value and/or the type of some fields. This is where `overrides` come in.
   *
   * e.g.
   *
   * {{{
   *   case class InputUser(firstName: String, lastName: String, age: String, address: String, job: String)
   *   case class OutputUser(name: String, age: Int, address: String, job: String)
   *
   *   val input = InputUser("Chris", "Birchall", "31", "1 Fake St", "Developer")
   *   val output = transform[InputUser, OutputUser](input,
   *     "name" -> s"${input.firstName} ${input.lastName}",
   *     "age" -> input.age.toInt
   *   )
   * }}}
   *
   * Here we have provided overrides to generate values for the `name` and `age` fields of `OutputUser`.
   * Anything that has not been overriden (i.e. `address` and `job`) will be copied from the input's fields.
   *
   * So this will produce an `OutputUser("Chris Birchall", 31, "1 Fake St", "Developer").
   *
   * The override mechanism is type-safe:
   *
   *  - if you're missing a necessary override, you'll get a compile error
   *  - if your override has the wrong type, you'll get a compile error
   *  - if you provide a superfluous override (i.e. no parameter with that name exists), you'll get a compile error
   *
   * Construction of the `To` instance is done by trying the following methods, in this order:
   *  - the `To` class's companion object's `apply` methods (tried in descending order of parameter count)
   *  - the `To` class's primary constructor
   */
  def transform[From, To](from: From, overrides: Tuple2[String, Any]*): To = macro Macros.transform[From, To]

}

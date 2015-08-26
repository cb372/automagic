import scala.language.experimental.macros

package object automagic {

  def transform[From, To](from: From, overrides: Tuple2[String, Any]*): To = macro Macros.transform[From, To]

}

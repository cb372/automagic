import scala.language.experimental.macros

package object automagic {

  def transform[From, To](from: From): To = macro Macros.transform[From, To]

}

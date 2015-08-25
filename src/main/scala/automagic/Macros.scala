package automagic

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

// TODO would be nice to use a macro bundle but compiler complained about escaping types
object Macros {

  def transform[From: c.WeakTypeTag, To: c.WeakTypeTag](c: Context)(from: c.Expr[From]) = {
    import c.universe._

    val fromType = weakTypeOf[From]
    val fromFields: Map[Name, Type] = findDeclaredFields(c)(fromType).toMap[Name, Type]

    val toType = weakTypeOf[To]
    val tpe = toType.typeSymbol

    if (tpe.isAbstract) c.abort(c.enclosingPosition, s"Cannot transform to type '$tpe' because it is not a concrete class.")

    val companionApply = findCompanionApplyMethod(c)(tpe)
    val constructor = companionApply getOrElse tpe.asClass.primaryConstructor.asMethod

    // so we can save the `from` tree to a val to avoid re-calculating it
    val tmpName = TermName(c.freshName())

    val args =
      constructor.paramLists map { paramList =>
        paramList map { param =>
          if (fromFields.contains(param.name.toTermName)) {
            q"""
           $tmpName.${param.name.toTermName}
           """
          } else q""" "bananas" """
        }
      }
    val construct =
      if (companionApply.isDefined)
        q"${tpe.companion}(...$args)"
      else
        q"new $tpe(...$args)"


    val code = q"""
      val $tmpName = $from
      $construct
     """
     println(code)
     code
  }

  private def findCompanionApplyMethod(c: Context)(tpe: c.universe.Symbol): Option[c.universe.MethodSymbol] = {
    import c.universe._

    if (tpe.companion.isModule) {
      // There is a companion object. Look for an apply method.
      // TODO arbitrarily chooses the first `apply` method it finds. Could try to choose intelligently if there is more than one.
      tpe.companion.typeSignature.members.collectFirst {
        case sym if sym.isMethod && sym.asMethod.name == TermName("apply") && sym.asMethod.returnType == tpe => sym.asMethod
      }
    } else None
  }

  private def findDeclaredFields(c: Context)(tpe: c.universe.Type): Iterable[(c.universe.Name, c.universe.Type)] =
    tpe.decls.collect {
      case field if field.asTerm.isVal => (c.universe.TermName(field.name.toString.trim), field.typeSignature)
    }

}

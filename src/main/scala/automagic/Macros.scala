package automagic

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

// TODO would be nice to use a macro bundle but compiler complained about escaping types
object Macros {

  def transform[From: c.WeakTypeTag, To: c.WeakTypeTag](c: Context)
                                                       (from: c.Expr[From], overrides: c.Expr[Tuple2[String, Any]]*) = {
    import c.universe._

    val fromType = weakTypeOf[From]
    val fromFields: Map[Name, Type] = findDeclaredFields(c)(fromType).toMap[Name, Type]

    val toType = weakTypeOf[To]
    val tpe = toType.typeSymbol

    if (tpe.isAbstract) c.abort(c.enclosingPosition, s"Cannot transform to type '$tpe' because it is not a concrete class.")

    val companionApply = findCompanionApplyMethod(c)(tpe)
    val constructor = companionApply getOrElse tpe.asClass.primaryConstructor.asMethod

    val overridesMap = collection.mutable.Map.empty[TermName, c.Tree]

    // Check that overrides have correct types and refer to actual parameter names
    overrides.foreach { ov =>
      val (key, value) = ov.tree match {
        case q"scala.this.Predef.ArrowAssoc[$_]($k).->[$_]($v)" => (k, v)
        case q"($k, $v)" => (k, v)
        case other => c.abort(c.enclosingPosition, "You must pass overrides as either key -> value or (key, value)")
      }
      val q"${keyAsString: String}" = key
      val paramName = TermName(keyAsString)
      val param = constructor.paramLists.flatten.find(_.name == paramName)
      if (param.isEmpty)
        c.abort(c.enclosingPosition,
        s"Cannot override parameter $key because it does not appear in the parameter lists for constructor of '$tpe'. Here are the parameters I'm trying to populate: ${constructor.paramLists}")
      else {
        val valueType = ov.actualType.typeArgs(1)
        val paramType = param.get.typeSignature
        if (!(valueType weak_<:< paramType))
          c.abort(c.enclosingPosition,
          s"Cannot override parameter $key with a value of type $valueType. Expected a value of type $paramType.")
      }

      overridesMap += paramName -> value.asInstanceOf[c.Tree]
    }

    // so we can save the `from` tree to a val to avoid re-calculating it
    val tmpName = TermName(c.freshName())

    val args =
      constructor.paramLists map { paramList =>
        paramList map { param =>
          val termName = param.name.toTermName
          if (overridesMap.contains(termName)) {
            q"${overridesMap(termName)}"
          } else if (fromFields.contains(termName)) {
           q"""
           $tmpName.${param.name.toTermName}
           """
          } else c.abort(c.enclosingPosition, s"Cannot find a value for parameter $termName")
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
     //println(code)
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

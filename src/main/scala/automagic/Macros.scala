package automagic

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

class Macros(val c: Context) {
  import c.universe._

  private case class MethodInfo(symbol: MethodSymbol, isOnCompanion: Boolean)
  private case class TreeWithActualType(tree: Tree, actualType: Type)

  private type MethodArgTrees = List[List[Tree]]

  def transform[From: c.WeakTypeTag, To: c.WeakTypeTag](from: c.Expr[From], overrides: c.Expr[Tuple2[String, Any]]*) = {
    val fromType = weakTypeOf[From]
    val fromFields: Map[Name, Type] = findDeclaredFieldsAndZeroArgMethods(fromType).toMap[Name, Type]

    val toType = weakTypeOf[To]
    val classSym = toType.typeSymbol
    val companionSym = classSym.companion

    if (classSym.isAbstract) c.abort(c.enclosingPosition, s"Cannot transform to type '$classSym' because it is not a concrete class.")

    val constructors = findAllConstructors(toType)

    // so we can save the `from` tree to a val to avoid re-calculating it
    val tmpName = TermName(c.freshName())

    val overridesMap: Map[TermName, TreeWithActualType] = parseOverrides(overrides)

    val results: Seq[(MethodInfo, Either[String, MethodArgTrees])] = constructors.map{ methodInfo =>
      methodInfo -> tryConstructor(methodInfo, tmpName, fromFields, overridesMap, companionSym)
    }
    val successfulResult = results collectFirst {
      case ((methodInfo, Right(args))) => methodInfo -> args
    }

    successfulResult match {
      case Some((methodInfo, args)) =>
        val construct =
          if (methodInfo.isOnCompanion)
            q"$companionSym(...$args)"
          else
            q"new $classSym(...$args)"

        val code = q"""
          val $tmpName = $from
          $construct
        """
        //println(code)
        code
      case None =>
        // Failed to build an invocation for any of the constructors. Fail with as useful a message as possible.
        c.abort(c.enclosingPosition, buildCompileErrorMsg(results, classSym))
    }
  }

  private def parseOverrides(overrides: Seq[c.Expr[(String, Any)]]): Map[TermName, TreeWithActualType] = {
    overrides.map { ov =>
      val (key, value) = ov.tree match {
        case q"scala.this.Predef.ArrowAssoc[$_]($k).->[$_]($v)" => (k, v)
        case q"($k, $v)" => (k, v)
        case other => c.abort(c.enclosingPosition, "You must pass overrides as either key -> value or (key, value)")
      }
      val q"${keyAsString: String}" = key
      val keyName = TermName(keyAsString)
      val actualValueType = ov.actualType.typeArgs(1)
      keyName -> TreeWithActualType(value.asInstanceOf[c.Tree], actualValueType)
    }.toMap
  }

  /**
   * Try to construct an invocation of the given constructor using the "from" instance's fields and the overrides.
   *
   * @param constructor the method whose invocation we want to build
   * @param fromName a name that can be used to refer to the "from" instance
   * @param fromFields the names and types of the "from" type's declared fields
   * @param overrides the
   * @return error message or the trees for the method arguments
   */
  private def tryConstructor(constructor: MethodInfo,
    fromName: TermName,
    fromFields: Map[Name, Type],
    overrides: Map[TermName, TreeWithActualType],
    toCompanion: Symbol): Either[String, List[List[Tree]]] = {

    /* Try to find a suitable tree to use for the given method param */
    def argumentFor(param: Symbol): Either[String, Tree] = {
      val termName = param.name.toTermName
      if (overrides.contains(termName)) {
        val tree = overrides(termName).tree
        // first try an override
        Right(q"$tree")
      } else if (fromFields.contains(termName) && (fromFields(termName) weak_<:< param.typeSignature)) {
        // next try a field of the "from" instance
        Right(q"$fromName.${param.name.toTermName}")
      } else if (param.asTerm.isParamWithDefault) {
        // finally try a default parameter
        val constructorMethodName = constructor.symbol.name.encodedName.toString.trim
        val i = constructor.symbol.paramLists.flatten.indexOf(param) + 1
        val defaultParamMethodName = TermName(s"$constructorMethodName$$default$$$i")
        Right(q"$toCompanion.$defaultParamMethodName")
      } else {
        // give up
        Left(s"Cannot find a suitable value for parameter '$termName'")
      }
    }

    checkOverrides(overrides, constructor.symbol).toLeft {
      val argss: List[Either[String, List[Tree]]] =
        constructor.symbol.paramLists map { paramList =>
          val args: List[Either[String, Tree]] = paramList map { param => argumentFor(param) }
          sequence(args)
        }
      sequence(argss)
    }.joinRight
  }

  /**
   * Validate the overrides against the given constructor.
   *
   * Overrides are valid for this constructor if there are no superfluous overrides
   * (i.e. overrides that don't correspond to a constructor parameter name)
   * and all override value's types match the type of their corresponding parameter.
   *
   * @return error message if validation failed
   */
  private def checkOverrides(overrides: Map[TermName, TreeWithActualType], constructor: MethodSymbol): Option[String] = {
    val results = for ((termName, value) <- overrides) yield {
      // Check that overrides have correct types and refer to actual parameter names
      val param = constructor.paramLists.flatten.find(_.name == termName)
      if (param.isEmpty)
          Some(s"Cannot override parameter '$termName' because it does not appear in the parameter lists")
      else {
        val paramType = param.get.typeSignature
        if (!(value.actualType weak_<:< paramType))
          Some(s"Cannot override parameter '$termName' with a value of type ${value.actualType}. Expected a value of type $paramType.")
        else
          None
      }
    }
    results.find(_.isDefined).flatten // if we found any problems, just return the first one
  }

  /* Build an error message saying what we tried */
  private def buildCompileErrorMsg(results: Seq[(MethodInfo, Either[String, MethodArgTrees])], classSym: Symbol) = {
    def resultToString(result: (MethodInfo, Either[String, MethodArgTrees])) = {
      val head =
        if (result._1.symbol.isConstructor)
          s"new ${classSym.name}"
        else
          s"${classSym.name}.${result._1.symbol.name.decodedName}"
      val paramLists = result._1.symbol.paramLists.map { paramList =>
        paramList.map { param => s"${param.name}: ${param.typeSignature}" }.mkString("(", ", ", ")")
      }.mkString
      val firstLine = s"$head$paramLists"
      val secondLine = result._2 match {
        case Left(error) => error
        case Right(_) => "OK"
      }
      s"""$firstLine
         |  ↳ $secondLine
       """.stripMargin
    }
    val errorDetails = results.map(resultToString).mkString("\n")
    s"""Failed to find any suitable constructors for $classSym. Tried the following:
       |
       |$errorDetails
     """.stripMargin
  }

  /**
   * Find all methods that can be used to construct an instance of type `tpe`,
   * in the order they should be tried.
   *
   * We want to try the companion object's `apply` methods first, in descending order of parameter count,
   * then try the class's primary constructor.
   *
   * @param tpe the type that we want to construct
   * @return
   */
  private def findAllConstructors(tpe: Type): Seq[MethodInfo] = {
    /**
     * Find all `apply` methods on the companion object, sorted in decreasing order of parameter count.
     */
    def findAllCompanionObjectApplyMethods(tpe: Type): Seq[MethodSymbol] = {
      val companion = tpe.typeSymbol.companion
      if (companion.isModule) {
        // There is a companion object. Look for all suitable `apply` methods.
        val applyMethods = companion.typeSignature.members.collect {
          case sym if sym.isMethod && sym.asMethod.name == TermName("apply") && sym.asMethod.returnType =:= tpe => sym.asMethod
        }
        applyMethods.toSeq.sortBy(_.paramLists.flatten.size).reverse
      } else Nil
    }

    val applyMethods = findAllCompanionObjectApplyMethods(tpe).map(sym => MethodInfo(sym, isOnCompanion = true))
    val classConstructors = List(MethodInfo(tpe.typeSymbol.asClass.primaryConstructor.asMethod, isOnCompanion = false))
    applyMethods ++ classConstructors
  }

  private def findDeclaredFieldsAndZeroArgMethods(tpe: Type): Iterable[(Name, Type)] = {
    tpe.decls.collect {
      case field if field.asTerm.isVal => (TermName(field.name.toString.trim), field.typeSignature)
      case method if method.asTerm.isMethod && method.asMethod.paramLists.isEmpty => (TermName(method.name.toString.trim), method.typeSignature.resultType)
    }
  }

  private def sequence[A, B](s: List[Either[A, B]]): Either[A, List[B]] = {
    s.foldRight(Right(Nil): Either[A, List[B]]) {
      (e, acc) => for (xs <- acc.right; x <- e.right) yield x :: xs
    }
  }

}

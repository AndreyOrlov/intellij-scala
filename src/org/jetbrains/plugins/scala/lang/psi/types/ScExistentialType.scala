package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.scala.collection.immutable.HashSet
import collection.mutable.ArrayBuffer
import api.statements.ScTypeAlias
import api.base.types.ScExistentialClause
import nonvalue._
import api.toplevel.typedef.ScTypeDefinition
import api.statements.params.ScTypeParam
import java.lang.ThreadLocal
import types.Conformance.AliasType
import collection.mutable

/**
* @author ilyas
*/
case class ScExistentialType(quantified : ScType,
                             wildcards : List[ScExistentialArgument]) extends ValueType {

  @volatile
  private var _boundNames: List[String] = null
  def boundNames: List[String] = {
    var res = _boundNames
    if (res != null) return res
    res = boundNamesInner
    _boundNames = res
    res
  }
  private def boundNamesInner: List[String] = wildcards.map {_.name}

  @volatile
  private var _substitutor: ScSubstitutor = null

  private def substitutor: ScSubstitutor = {
    var res = _substitutor
    if (res != null) return res
    res = substitutorInner
    _substitutor = res
    res
  }
  def substitutorInner: ScSubstitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""),
    ScSkolemizedType(p.name, p.args, p.lowerBound, p.upperBound))}

  @volatile
  private var _skolem: ScType = null

  def skolem: ScType = {
    var res = _skolem
    if (res != null) return res
    res = skolemInner
    _skolem = res
    res
  }

  private def skolemInner: ScType = {
    def unpack(ex: ScExistentialArgument, deep: Int = 2): ScSkolemizedType = {
      if (deep == 0) {
        ScSkolemizedType(ex.name, ex.args, types.Nothing, types.Any)
      } else {
        val unpacked = wildcards.map(unpack(_, deep - 1))
        val skolemSubst = unpacked.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p)}
        ScSkolemizedType(ex.name, ex.args, skolemSubst.subst(ex.lowerBound), skolemSubst.subst(ex.upperBound))
      }
    }
    val unpacked = wildcards.map(w => (w, unpack(w))).toMap
    val skolemSubst = unpacked.values.foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p.name, ""), p)}
    skolemSubst.subst(quantified).recursiveUpdate {
      case s: ScSkolemizedType => (true, s)
      case tp =>
        Conformance.isAliasType(tp) match {
          case Some(AliasType(ta, lower, upper)) if ta.isExistentialTypeAlias && wildcards.find(_.name == ta.name).isDefined =>
            val argument = wildcards.find(_.name == ta.name).get
            (true, unpacked.getOrElse(argument, argument.unpack))
          case _ => (false, tp)
        }
    }
  }

  override def removeAbstracts = ScExistentialType(quantified.removeAbstracts, 
    wildcards.map(_.removeAbstracts))

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        try {
          ScExistentialType(quantified.recursiveUpdate(update, newVisited),
            wildcards.map(_.recursiveUpdate(update, newVisited)))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        try {
          ScExistentialType(quantified.recursiveVarianceUpdate(update, variance),
            wildcards.map(_.recursiveVarianceUpdate(update, variance)))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    val simplified = simplify()
    if (this != simplified) return Equivalence.equivInner(simplified, r, undefinedSubst, falseUndef)
    r match {
      case ex: ScExistentialType => {
        val simplified = ex.simplify()
        if (ex != simplified) return Equivalence.equivInner(this, simplified, undefinedSubst, falseUndef)
        val list = wildcards.zip(ex.wildcards)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = w2.equivInner(w1, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        Equivalence.equivInner(substitutor.subst(quantified), ex.substitutor.subst(ex.quantified), undefinedSubst, falseUndef)
      }
      case _ => (false, undefinedSubst)
    }
  }

  def wildcardsMap(): mutable.HashMap[ScExistentialArgument, Seq[ScType]] = {
    val res = mutable.HashMap.empty[ScExistentialArgument, Seq[ScType]]
    def checkRecursive(tp: ScType, rejected: HashSet[String]) {
      tp match {
        case JavaArrayType(arg) => checkRecursive(arg, rejected)
        case ScAbstractType(tpt, lower, upper) =>
          checkRecursive(tpt, rejected)
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
        case c@ScCompoundType(comps, decls, typeDecls, _) =>
          val newSet = rejected ++ typeDecls.map(_.name)
          comps.foreach(checkRecursive(_, newSet))
          c.signatureMap.foreach(tuple => checkRecursive(tuple._2, newSet))
          c.types.foreach(tuple => {
            checkRecursive(tuple._2._1, newSet)
            checkRecursive(tuple._2._1, newSet)
          })
        case ScDesignatorType(elem) =>
          elem match {
            case ta: ScTypeAlias if ta.getContext.isInstanceOf[ScExistentialClause] =>
              wildcards.foreach(arg => if (arg.name == ta.name && !rejected.contains(arg.name)) {
                res.update(arg, res.getOrElse(arg, Seq.empty[ScType]) ++ Seq(tp))
              })
            case _ =>
          }
        case ScTypeVariable(name) =>
          wildcards.foreach(arg => if (arg.name == name && !rejected.contains(arg.name)) {
            res.update(arg, res.getOrElse(arg, Seq.empty[ScType]) ++ Seq(tp))
          })
        case ex: ScExistentialType =>
          var newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
          checkRecursive(ex.quantified, newSet)
          if (ex eq this) newSet = rejected ++ ex.wildcards.map(_.name)
          ex.wildcards.foreach(ex => {
            checkRecursive(ex.lowerBound, newSet)
            checkRecursive(ex.upperBound, newSet)
          })
        case ScFunctionType(returnType, params) =>
          checkRecursive(returnType, rejected)
          params.foreach(checkRecursive(_, rejected))
        case ScTupleType(components) =>
          components.foreach(checkRecursive(_, rejected))
        case ScProjectionType(projected, element, subst, _) =>
          checkRecursive(projected, rejected)
        case ScParameterizedType(designator, typeArgs) =>
          checkRecursive(designator, rejected)
          typeArgs.foreach(checkRecursive(_, rejected))
        case ScTypeParameterType(name, args, lower, upper, param) =>
        //          checkRecursive(lower.v, rejected)
        //          checkRecursive(upper.v, rejected)
        //          args.foreach(checkRecursive(_, rejected))
        case ScSkolemizedType(name, args, lower, upper) =>
          checkRecursive(lower, rejected)
          checkRecursive(upper, rejected)
          args.foreach(checkRecursive(_, rejected))
        case ScUndefinedType(tpt) => checkRecursive(tpt, rejected)
        case ScMethodType(returnType, params, isImplicit) =>
          checkRecursive(returnType, rejected)
          params.foreach(p => checkRecursive(p.paramType, rejected))
        case ScTypePolymorphicType(internalType, typeParameters) =>
          checkRecursive(internalType, rejected)
          typeParameters.foreach(tp => {
            checkRecursive(tp.lowerType, rejected)
            checkRecursive(tp.upperType, rejected)
          })
        case _ =>
      }
    }
    checkRecursive(this, HashSet.empty)
    wildcards.foreach {
      case ScExistentialArgument(_, args, lower, upper) =>
        checkRecursive(lower, HashSet.empty)
        checkRecursive(upper, HashSet.empty)
    }
    res
  }

  /** Specification 3.2.10:
    * 1. Multiple for-clauses in an existential type can be merged. E.g.,
    * T forSome {Q} forSome {H} is equivalent to T forSome {Q;H}.
    * 2. Unused quantifications can be dropped. E.g., T forSome {Q;H} where
    * none of the types defined in H are referred to by T or Q, is equivalent to
    * T forSome {Q}.
    * 3. An empty quantification can be dropped. E.g., T forSome { } is equivalent
    * to T.
    * 4. An existential type T forSome {Q} where Q contains a clause
    * type t[tps] >: L <: U is equivalent to the type T' forSome {Q} where
    * T' results from T by replacing every covariant occurrence (4.5) of t in T by
    * U and by replacing every contravariant occurrence of t in T by L.
    */
  def simplify(): ScType = {
    //second rule
    val usedWildcards = wildcardsMap().keySet

    val used = wildcards.filter(arg => usedWildcards.contains(arg))
    if (used.length == 0) return quantified
    if (used.length != wildcards.length) return ScExistentialType(quantified, used).simplify()

    //first rule
    quantified match {
      case ScExistentialType(quantified, wildcards) =>
        return ScExistentialType(quantified, wildcards ++ this.wildcards).simplify()
      case _ =>
    }

    //third rule
    if (wildcards.length == 0) return quantified

    var updated = false
    //fourth rule
    def hasWildcards(tp: ScType): Boolean = {
      var res = false
      tp.recursiveUpdate {
        case tp@ScDesignatorType(element) => element match {
          case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause]
            && wildcards.find(_.name == a.name) != None =>
            res = true
            (res, tp)
          case _ => (res,  tp)
        }
        case tp@ScTypeVariable(name) if wildcards.find(_.name == name) != None =>
          res = true
          (res, tp)
        case tp: ScType => (res, tp)
      }
      res
    }

    def updateRecursive(tp: ScType, rejected: HashSet[String], variance: Int): ScType = {
      if (variance == 0) return tp //optimization
      tp match {
        case _: StdType => tp
        case f@ScFunctionType(returnType, params) =>
          ScFunctionType(updateRecursive(returnType, rejected, variance),
            params.map(updateRecursive(_, rejected, -variance)))(f.getProject, f.getScope)
        case t@ScTupleType(components) =>
          ScTupleType(components.map(updateRecursive(_, rejected, variance)))(t.getProject, t.getScope)
        case c@ScCompoundType(components, decls, typeDecls, subst) =>
          val newSet = rejected ++ typeDecls.map(_.name)
          new ScCompoundType(components, decls, typeDecls, subst, c.signatureMap.map {
            case (sign, tp) => (sign, updateRecursive(tp, newSet, variance))
          }, c.types.map {
            case (s, (tp1, tp2)) => (s, (updateRecursive(tp1, newSet, variance), updateRecursive(tp2, newSet, -variance)))
          }, c.problems.toList)
        case ScProjectionType(_, _, _, _) => tp
        case JavaArrayType(_) => tp
        case ScParameterizedType(designator, typeArgs) =>
          val parameteresIterator = designator match {
            case tpt: ScTypeParameterType =>
              tpt.args.map(_.param).iterator
            case undef: ScUndefinedType =>
              undef.tpt.args.map(_.param).iterator
            case tp: ScType =>
              ScType.extractClass(tp) match {
                case Some(owner) => {
                  owner match {
                    case td: ScTypeDefinition => td.typeParameters.iterator
                    case _ => owner.getTypeParameters.iterator
                  }
                }
                case _ => return tp
              }
          }
          val typeArgsIterator = typeArgs.iterator
          val newTypeArgs = new ArrayBuffer[ScType]()
          while (parameteresIterator.hasNext && typeArgsIterator.hasNext) {
            val param = parameteresIterator.next()
            val arg = typeArgsIterator.next()
            param match {
              case tp: ScTypeParam if tp.isCovariant =>
                newTypeArgs += updateRecursive (arg, rejected, variance)
              case tp: ScTypeParam if tp.isContravariant =>
                newTypeArgs += updateRecursive (arg, rejected, -variance)
              case _ =>
                newTypeArgs += arg
            }
          }
          ScParameterizedType(designator, newTypeArgs)
        case ex@ScExistentialType(quantified, wildcards) =>
          var newSet = if (ex ne this) rejected ++ ex.wildcards.map(_.name) else rejected
          val q = updateRecursive(quantified, newSet, variance)
          if (ex eq this) newSet = rejected ++ ex.wildcards.map(_.name)
          ScExistentialType(q, wildcards.map(arg => ScExistentialArgument(arg.name, arg.args.map(arg =>
              updateRecursive(arg, newSet, -variance).asInstanceOf[ScTypeParameterType]),
              updateRecursive(arg.lowerBound, newSet, -variance), updateRecursive(arg.upperBound, newSet, variance))))
        case ScThisType(clazz) => tp
        case ScDesignatorType(element) => element match {
          case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
            if (!rejected.contains(a.name)) {
              wildcards.find(_.name == a.name) match {
                case Some(arg) => variance match {
                  case 1 if !hasWildcards(arg.upperBound)=>
                    updated = true
                    arg.upperBound
                  case -1 if !hasWildcards(arg.lowerBound)=>
                    updated = true
                    arg.lowerBound
                  case _ => tp
                }
                case _ => tp
              }
            } else tp
          case _ => tp
        }
        case ScTypeVariable(name) =>
          if (!rejected.contains(name)) {
            wildcards.find(_.name == name) match {
              case Some(arg) => variance match {
                case 1 if !hasWildcards(arg.upperBound) =>
                  updated = true
                  arg.upperBound
                case -1 if !hasWildcards(arg.lowerBound) =>
                  updated = true
                  arg.lowerBound
                case _ => tp
              }
              case _ => tp
            }
          } else tp
        case ScTypeParameterType(name, args, lower, upper, param) =>
          //should return TypeParameterType (for undefined type)
          tp
          /*ScTypeParameterType(name, args.map(arg =>
            updateRecursive(arg, rejected, -variance).asInstanceOf[ScTypeParameterType]),
            new Suspension[ScType](updateRecursive(lower.v, rejected, -variance)),
            new Suspension[ScType](updateRecursive(upper.v, rejected, variance)), param)*/
        case ScSkolemizedType(name, args, lower, upper) =>
          ScSkolemizedType(name, args.map(arg =>
            updateRecursive(arg, rejected, -variance).asInstanceOf[ScTypeParameterType]),
            updateRecursive(lower, rejected, -variance),
            updateRecursive(upper, rejected, variance))
        case ScUndefinedType(tpt) => ScUndefinedType(
          updateRecursive(tpt, rejected, variance).asInstanceOf[ScTypeParameterType]
        )
        case m@ScMethodType(returnType, params, isImplicit) =>
          ScMethodType(updateRecursive(returnType, rejected, variance),
            params.map(param => param.copy(paramType = updateRecursive(param.paramType, rejected, -variance))),
            isImplicit)(m.project, m.scope)
        case ScAbstractType(tpt, lower, upper) =>
          ScAbstractType(updateRecursive(tpt, rejected, variance).asInstanceOf[ScTypeParameterType],
            updateRecursive(lower, rejected, -variance),
            updateRecursive(upper, rejected, variance))
        case ScTypePolymorphicType(internalType, typeParameters) =>
          ScTypePolymorphicType(
            updateRecursive(internalType, rejected, variance),
            typeParameters.map(tp => TypeParameter(tp.name,
              updateRecursive(tp.lowerType, rejected, variance),
              updateRecursive(tp.upperType, rejected, variance),
              tp.ptp
            ))
          )
        case _ => tp
      }
    }

    val res = updateRecursive(this, HashSet.empty, 1)
    if (updated) {
      res match {
        case ex: ScExistentialType if ex != this => ex.simplify()
        case _ => res
      }
    } else this
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitExistentialType(this)
  }
}

object ScExistentialType {
  def simpleExistential(name: String, args: List[ScTypeParameterType], lowerBound: ScType, upperBound: ScType): ScExistentialType = {
    ScExistentialType(ScTypeVariable(name), List(ScExistentialArgument(name, args, lowerBound, upperBound)))
  }
}

case class ScExistentialArgument(name : String, args : List[ScTypeParameterType],
                                 lowerBound : ScType, upperBound : ScType) {
  def unpack = new ScSkolemizedType(name, args, lowerBound, upperBound)

  def removeAbstracts: ScExistentialArgument = ScExistentialArgument(name, args, lowerBound.removeAbstracts, upperBound.removeAbstracts)

  def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScExistentialArgument = {
    ScExistentialArgument(name, args, lowerBound.recursiveUpdate(update, visited), upperBound.recursiveUpdate(update, visited))
  }

  def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScExistentialArgument = {
    ScExistentialArgument(name, args, lowerBound.recursiveVarianceUpdate(update, -variance),
      upperBound.recursiveVarianceUpdate(update, variance))
  }

  def equivInner(exist: ScExistentialArgument, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    val s = (exist.args zip args).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT ((p._1.name, ""), p._2)}
    val t = Equivalence.equivInner(lowerBound, s.subst(exist.lowerBound), undefinedSubst, falseUndef)
    if (!t._1) return (false, undefinedSubst)
    undefinedSubst = t._2
    Equivalence.equivInner(upperBound, s.subst(exist.upperBound), undefinedSubst, falseUndef)
  }

  def subst(substitutor: ScSubstitutor): ScExistentialArgument = {
    ScExistentialArgument(name, args.map(t => substitutor.subst(t).asInstanceOf[ScTypeParameterType]),
      substitutor subst lowerBound, substitutor subst upperBound)
  }
}

case class ScSkolemizedType(name : String, args : List[ScTypeParameterType], lower : ScType, upper : ScType)
  extends ValueType {
  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitSkolemizedType(this)
  }

  override def removeAbstracts = ScSkolemizedType(name, args, lower.removeAbstracts, upper.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        ScSkolemizedType(name, args, lower.recursiveUpdate(update, newVisited), upper.recursiveUpdate(update, newVisited))
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        ScSkolemizedType(name, args, lower.recursiveVarianceUpdate(update, -variance),
          upper.recursiveVarianceUpdate(update, variance))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var u = uSubst
    r match {
      case ScSkolemizedType(rname, rargs, rlower, rupper) =>
        if (args.length != rargs.length) return (false, uSubst)
        args.zip(rargs) foreach {
          case (tpt1, tpt2) =>
            val t = Equivalence.equivInner(tpt1, tpt2, u, falseUndef)
            if (!t._1) return (false, u)
            u = t._2
        }
        var t = Equivalence.equivInner(lower, rlower, u, falseUndef)
        if (!t._1) return (false, u)
        u = t._2
        t = Equivalence.equivInner(upper, rupper, u, falseUndef)
        if (!t._1) return (false, u)
        u = t._2
        (true, u)
      case _ => (false, uSubst)
    }
  }
}

package org.jetbrains.plugins.scala
package lang
package psi
package types

import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import nonvalue.{ScTypePolymorphicType, NonValueType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.Misc._
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import collection.Seq
import collection.mutable.{MultiMap, HashMap}
import lang.resolve.ScalaResolveResult

object Conformance {

  @deprecated("Use conforms(l, r, true) instead")
  def weakConforms(l: ScType, r: ScType): Boolean = {
    (l, r) match {
      case (Byte, Short | Char | Int | Long | Float | Double) => true
      case (Short, Int | Long | Float | Double) => true
      case (Int, Long | Float | Double) => true
      case (Long, Float | Double) => true
      case (Float, Double) => true
      case (_, _) => conforms(l, r)
    }
  }

  /**
   * Checks, whether the following assignment is correct:
   * val x: l = (y: r) 
   */
  def conforms(l: ScType, r: ScType, checkWeak: Boolean = false): Boolean =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, false, checkWeak)._1

  def undefinedSubst(l: ScType, r: ScType, checkWeak: Boolean = false): ScUndefinedSubstitutor =
    conformsInner(l, r, HashSet.empty, new ScUndefinedSubstitutor, false, checkWeak)._2

  private def conformsInner(l: ScType, r: ScType, visited: Set[PsiClass], subst: ScUndefinedSubstitutor,
                            noBaseTypes: Boolean = false, //todo: remove, not used
                            checkWeak: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled

    var undefinedSubst: ScUndefinedSubstitutor = subst

    if (checkWeak) {
      (l, r) match {
        case (Byte, Short | Char | Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Short, Int | Long | Float | Double) => return (true, undefinedSubst)
        case (Int, Long | Float | Double) => return (true, undefinedSubst)
        case (Long, Float | Double) => return (true, undefinedSubst)
        case (Float, Double) => return (true, undefinedSubst)
        case _ => 
      }
    }
    (l, r) match {
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level > u1.level =>
        return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
      case (u2: ScUndefinedType, u1: ScUndefinedType) if u2.level > u1.level =>
        return (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), u1))
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level == u1.level => return (true, undefinedSubst)
      case (u: ScUndefinedType, tp: ScType) => return (true, undefinedSubst.addLower((u.tpt.name, u.tpt.getId), tp))
      case (tp: ScType, u: ScUndefinedType) => return (true, undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), tp))
      case _ => {
        val isEquiv = Equivalence.equivInner(l, r, undefinedSubst)
        if (isEquiv._1) {
          isEquiv._2.getSubstitutor match {
            case Some(s) => if (s.subst(l).equiv(s.subst(r))) return isEquiv
            case _ =>
          }
        }
      }
    }

    (l, r) match {
      case (ScMethodType(returnType1, params1, _), ScMethodType(returnType2, params2, _)) => {
        if (params1.length != params2.length) return (false, undefinedSubst)
        var t = conformsInner(returnType1, returnType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        var i = 0
        while (i < params1.length) {
          if (params1(i).isRepeated != params2(i).isRepeated) return (false, undefinedSubst)
          t = Equivalence.equivInner(params1(i).paramType, params2(i).paramType, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
      }
      case (ScTypePolymorphicType(internalType1, typeParameters1), ScTypePolymorphicType(internalType2, typeParameters2)) => {
        if (typeParameters1.length != typeParameters2.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters1.length) {
          var t = conformsInner(typeParameters1(i).lowerType, typeParameters2(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          t = conformsInner(typeParameters2(i).upperType, typeParameters1(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        import Suspension._
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, String), ScType] ++ typeParameters1.zip(typeParameters2).map({
          tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)),
                  new ScTypeParameterType(tuple._2.name, List.empty, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, Map.empty)
        val t = conformsInner(subst.subst(internalType1), internalType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        (true, undefinedSubst)
      }
      //todo: ScTypeConstructorType
      case (_: NonValueType, _) => return (false, undefinedSubst)
      case (_, _: NonValueType) => return (false, undefinedSubst)
      case (Any, _) => return (true, undefinedSubst)
      case (_, Nothing) => return (true, undefinedSubst)
      /*
        this case for checking: val x: T = null
        This is good if T class type: T <: AnyRef and !(T <: NotNull)
       */
      case (_, Null) => {
        if (!conforms(AnyRef, l)) return (false, undefinedSubst)
        ScType.extractDesignated(l) match {
          case Some((el, _)) => {
            val notNullClass = JavaPsiFacade.getInstance(el.getProject).findClass("scala.NotNull", el.getResolveScope)
            val notNullType = ScDesignatorType(notNullClass)
            return (!conforms(notNullType, l), undefinedSubst) //todo: think about undefinedSubst
          }
          case _ => return (true, undefinedSubst)
        }
      }
      case (tpt: ScTypeParameterType, _) => return conformsInner(tpt.lower.v, r, HashSet.empty, undefinedSubst)
      case (_, tpt: ScTypeParameterType) => return conformsInner(l, tpt.upper.v, HashSet.empty, undefinedSubst)
      case (Null, _) => return (r == Nothing, undefinedSubst)
      case (AnyRef, Any) => return (false, undefinedSubst)
      case (AnyRef, AnyVal) => return (false, undefinedSubst)
      case (AnyRef, _: ValType) => return (false, undefinedSubst)
      case (AnyRef, _) => return (true, undefinedSubst)
      case (Singleton, _: ScSingletonType) => return (true, undefinedSubst)
      case (Singleton, _) => return (false, undefinedSubst)
      case (AnyVal, _: ValType) => return (true, undefinedSubst)
      case (AnyVal, _) => return (false, undefinedSubst)
      case (ScTupleType(comps1: Seq[ScType]), ScTupleType(comps2: Seq[ScType])) => {
        if (comps1.length != comps2.length) return (false, undefinedSubst)
        var i = 0
        while (i < comps1.length) {
          val comp1 = comps1(i)
          val comp2 = comps2(i)
          val t = conformsInner(comp1, comp2, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          else undefinedSubst = t._2
          i = i + 1
        }
        return (true, undefinedSubst)
      }
      case (fun: ScFunctionType, _) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conformsInner(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, fun: ScFunctionType) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conformsInner(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (tuple: ScTupleType, _) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conformsInner(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, tuple: ScTupleType) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conformsInner(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (JavaArrayType(arg1), JavaArrayType(arg2)) => {
        val argsPair = (arg1, arg2)
        argsPair match {
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        val argsPair = (arg, args(0))
        argsPair match {
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (ScParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        val argsPair = (arg, args(0))
        argsPair match {
          case (u: ScUndefinedType, rt) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
          }
          case (lt, u: ScUndefinedType) => {
            undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
            undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
          }
          case (_: ScExistentialArgument, _) => {
            val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
            if (!y._1) return (false, undefinedSubst)
            else undefinedSubst = y._2
          }
          case _ => {
            val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst)
            if (!t._1) return (false, undefinedSubst)
            undefinedSubst = t._2
          }
        }
        return (true, undefinedSubst)
      }
      case (ScParameterizedType(owner: ScUndefinedType, args1), ScParameterizedType(owner1: ScType, args2)) => {
        return (true, undefinedSubst.addLower((owner.tpt.name, owner.tpt.getId), r))
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScUndefinedType, args2)) => {
        return (true, undefinedSubst.addUpper((owner1.tpt.name, owner1.tpt.getId), l))
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScType, args2))
        if owner.equiv(owner1) => {
        if (args1.length != args2.length) return (false, undefinedSubst)
        ScType.extractClass(owner) match {
          case Some(owner) => {
            val parametersIterator = owner.getTypeParameters.iterator
            val args1Iterator = args1.iterator
            val args2Iterator = args2.iterator
            while (parametersIterator.hasNext && args1Iterator.hasNext && args2Iterator.hasNext) {
              val tp = parametersIterator.next
              val argsPair = (args1Iterator.next, args2Iterator.next)
              tp match {
                case scp: ScTypeParam if (scp.isContravariant) => {
                  val y = Conformance.conformsInner(argsPair._2, argsPair._1, HashSet.empty, undefinedSubst)
                  if (!y._1) return (false, undefinedSubst)
                  else undefinedSubst = y._2
                }
                case scp: ScTypeParam if (scp.isCovariant) => {
                  val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                  if (!y._1) return (false, undefinedSubst)
                  else undefinedSubst = y._2
                }
                //this case filter out such cases like undefined type
                case _ => {
                  argsPair match {
                    case (u: ScUndefinedType, rt) => {
                      undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), rt)
                      undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), rt)
                    }
                    case (lt, u: ScUndefinedType) => {
                      undefinedSubst = undefinedSubst.addLower((u.tpt.name, u.tpt.getId), lt)
                      undefinedSubst = undefinedSubst.addUpper((u.tpt.name, u.tpt.getId), lt)
                    }
                    case (_: ScExistentialArgument, _) => {
                      val y = Conformance.conformsInner(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                      if (!y._1) return (false, undefinedSubst)
                      else undefinedSubst = y._2
                    }
                    case _ => {
                      val t = Equivalence.equivInner(argsPair._1, argsPair._2, undefinedSubst)
                      if (!t._1) return (false, undefinedSubst)
                      undefinedSubst = t._2
                    }
                  }
                }
              }
            }
            return (true, undefinedSubst)
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case (c@ScCompoundType(comps, decls, types, _), _) => {
        return (comps.forall(tp => {
          val t = conformsInner(r, tp, HashSet.empty, undefinedSubst)
          undefinedSubst = t._2
          t._1
        }) && (ScType.extractClassType(r) match {
          case Some((clazz, subst)) => {
            if (!decls.isEmpty || (comps.isEmpty && decls.isEmpty && types.isEmpty)) { //if decls not empty or it's synthetic created
              val sigs = getSignatureMap(clazz)
              for ((sig, t) <- c.signatureMap) {
                sigs.get(sig) match {
                  case None => return (false, undefinedSubst)
                  case Some(t1) => {
                    val tt = conformsInner(t, subst.subst(t1), HashSet.empty, undefinedSubst)
                    if (!tt._1) return (false, undefinedSubst)
                    else undefinedSubst = tt._2
                  }
                }
              }
            }
            if (!types.isEmpty) {
              val hisTypes = TypeDefinitionMembers.getTypes(clazz)
              for (t <- types) {
                hisTypes.get(t) match {
                  case None => false
                  case Some(n) => {
                    val subst1 = n.substitutor
                    n.info match {
                      case ta: ScTypeAlias => {
                        val s = subst1 followed subst
                        val tt = conformsInner(t.upperBound.getOrElse(Any), s.subst(ta.upperBound.getOrElse(Any)), HashSet.empty, undefinedSubst)
                        if (!tt._1) return (false, undefinedSubst)
                        else undefinedSubst = tt._2
                        val tt2 = conformsInner(s.subst(ta.lowerBound.getOrElse(Nothing)), t.lowerBound.getOrElse(Nothing), HashSet.empty, undefinedSubst)
                        if(!tt2._1) return (false, undefinedSubst)
                        else undefinedSubst = tt2._2
                      }
                      case inner: PsiClass => {
                        val des = ScParameterizedType.create(inner, subst1 followed subst)
                        val tt = conformsInner(t.upperBound.getOrElse(Any), subst.subst(des), HashSet.empty, undefinedSubst)
                        if (!tt._1) return (false, undefinedSubst)
                        else undefinedSubst = tt._2
                        val tt2 = conformsInner(des, t.lowerBound.getOrElse(Nothing), HashSet.empty, undefinedSubst)
                        if (!tt2._1) return (false, undefinedSubst)
                        else undefinedSubst = tt2._2
                      }
                    }
                  }
                }
              }
            }
            true
          }
          case None => r match {
            case c1@ScCompoundType(comps1, _, _, _) => comps1.forall(tp => {
              val t = conformsInner(tp, c, HashSet.empty, undefinedSubst)
              undefinedSubst = t._2
              t._1
            }) && (
                    c1.signatureMap.forall {
                      p => {
                        val s1 = p._1
                        val rt1 = p._2
                        c.signatureMap.get(s1) match {
                          case None => comps.find {
                            t => ScType.extractClassType(t) match {
                              case None => false
                              case Some((clazz, subst)) => {
                                val classSigs = getSignatureMap(clazz)
                                classSigs.get(s1) match {
                                  case None => false
                                  case Some(rt) => {
                                    val t = conformsInner(subst.subst(rt), rt1, HashSet.empty, undefinedSubst)
                                    undefinedSubst = t._2
                                    t._1
                                  }
                                }
                              }
                            }
                          }
                          case Some(rt) => {
                            val t = conformsInner(rt, rt1, HashSet.empty, undefinedSubst)
                            undefinedSubst = t._2
                            t._1
                          }
                        }
                        //todo check for refinement's type decls
                      }
                    })
            case _ => false
          }
        }), undefinedSubst)
      }

      case (ScSkolemizedType(_, _, lower, _), _) => return conformsInner(lower, r, HashSet.empty, undefinedSubst)
      case (ScPolymorphicType(_, _, lower, _), _) => return conformsInner(lower.v, r, HashSet.empty, undefinedSubst) //todo implement me
      case (ScExistentialArgument(_, params1, lower1, upper1), ScExistentialArgument(_, params2, lower2, upper2))
        if params1.isEmpty && params2.isEmpty => {
        var t = conformsInner(upper1, upper2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        t = conformsInner(lower2, lower1, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        return (true, undefinedSubst)
      }
      case (ScExistentialArgument(_, params, lower, upper), _) if params.isEmpty => return conformsInner(upper, r, HashSet.empty, undefinedSubst)
      case (ex@ScExistentialType(q, wilds), _) => return conformsInner(ex.substitutor.subst(q), r, HashSet.empty, undefinedSubst)
      case (_, s: ScSingletonType) => {
        ScType.extractClass(l) match {
          case Some(clazz) if clazz.getQualifiedName == "scala.Singleton" => return (true, undefinedSubst)
          case _ => if (conforms(l, s.pathType)) return (true, undefinedSubst)
        }
      }
      case (_, ScSkolemizedType(_, _, _, upper)) => return conformsInner(l, upper, HashSet.empty, undefinedSubst)
      case (_, ScCompoundType(comps, _, _, _)) => {
        val iterator = comps.iterator
        while (iterator.hasNext) {
          val comp = iterator.next
          val t = conformsInner(l, comp, HashSet.empty, undefinedSubst)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }
      case (_, ScExistentialArgument(_, params, _, upper)) if params.isEmpty => return conformsInner(l, upper, HashSet.empty, undefinedSubst)
      case (_, ex: ScExistentialType) => return conformsInner(l, ex.skolem, HashSet.empty, undefinedSubst)
      case (_, proj: ScProjectionType) => {
        proj.element match {
          case Some(syntheticClass: ScSyntheticClass) => return conformsInner(l, syntheticClass.t, HashSet.empty, undefinedSubst)
          case _ =>
        }
      }
      case (_, ScPolymorphicType(_, _, _, upper)) => {
        val uBound = upper.v
        ScType.extractClass(uBound) match {
          case Some(pc) if visited.contains(pc) => return conformsInner(l, ScDesignatorType(pc), visited + pc, undefinedSubst)
          case Some(pc) => return conformsInner(l, uBound, visited + pc, undefinedSubst)
          case None => return conformsInner(l, uBound, visited, undefinedSubst)
        }
      }
      case _ =>
    }
    if (noBaseTypes) return (false, undefinedSubst)
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return (false, undefinedSubst)
      case Some((rClass: PsiClass, subst: ScSubstitutor)) => {
        ScType.extractClass(l) match {
          case Some(lClass) => {
            if (rClass.getQualifiedName == "java.lang.Object" ) {
              return conformsInner(l, AnyRef, visited, undefinedSubst, noBaseTypes)
            } else if (lClass.getQualifiedName == "java.lang.Object") {
              return conformsInner(AnyRef, r, visited, undefinedSubst, noBaseTypes)
            }
            val inh = smartIsInheritor(rClass, subst, lClass)
            if (!inh._1) return (false, undefinedSubst)
            val tp = inh._2
            //Special case for higher kind types passed to generics.
            if (lClass.getTypeParameters.length > 0) {
              l match {
                case p: ScParameterizedType =>
                case f: ScFunctionType =>
                case t: ScTupleType =>
                case _ => return (true, undefinedSubst)
              }
            }
            val t = conformsInner(l, tp, visited + rClass, undefinedSubst, true)
            if (t._1) return (true, t._2)
            else return (false, undefinedSubst)
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case _ => {
        var bases: Seq[ScType] = BaseTypes.get(r)
        val iterator = bases.iterator
        while (iterator.hasNext) {
          ProgressManager.checkCanceled
          val tp = iterator.next
          val t = conformsInner(l, tp, visited, undefinedSubst, true)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }//return BaseTypes.get(r).find {t => conforms(l, t, visited)}
    }
  }

  def getSignatureMapInner(clazz: PsiClass): HashMap[Signature, ScType] = {
    val m = new HashMap[Signature, ScType]
    val iterator = TypeDefinitionMembers.getSignatures(clazz).iterator
    while (iterator.hasNext) {
      val (full, _) = iterator.next
      m += ((full.sig, full.retType))
    }
    m
  }

  def getSignatureMap(clazz: PsiClass): HashMap[Signature, ScType] = {
    CachesUtil.get(
      clazz, CachesUtil.SIGNATURES_MAP_KEY,
      new CachesUtil.MyProvider(clazz, {clazz: PsiClass => getSignatureMapInner(clazz)})
        (PsiModificationTracker.MODIFICATION_COUNT)
      )
  }


  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass) : (Boolean, ScType) = {
    smartIsInheritor(leftClass, substitutor, rightClass, new collection.mutable.HashSet[PsiClass])
  }
  private def smartIsInheritor(leftClass: PsiClass, substitutor: ScSubstitutor, rightClass: PsiClass,
                               visited: collection.mutable.HashSet[PsiClass]): (Boolean, ScType) = {
    ProgressManager.checkCanceled
    val bases: Seq[Any] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes
    }
    val iterator = bases.iterator
    var res: ScType = null
    while (iterator.hasNext) {
      val tp: ScType = iterator.next match {
        case tp: ScType => substitutor.subst(tp)
        case pct: PsiClassType => substitutor.subst(ScType.create(pct, leftClass.getProject))
      }
      ScType.extractClassType(tp) match {
        case Some((clazz: PsiClass, _)) if visited.contains(clazz) =>
        case Some((clazz: PsiClass, subst)) if clazz == rightClass => {
          visited += clazz
          if (res == null) res = tp
          else if (tp.conforms(res)) res = tp
        }
        case Some((clazz: PsiClass, subst)) => {
          visited += clazz
          val recursive = smartIsInheritor(clazz, subst, rightClass, visited)
          if (recursive._1) {
            if (res == null) res = recursive._2
            else if (recursive._2.conforms(res)) res = recursive._2
          }
        }
        case _ =>
      }
    }
    if (res == null) (false, null)
    else (true, res)
  }
}
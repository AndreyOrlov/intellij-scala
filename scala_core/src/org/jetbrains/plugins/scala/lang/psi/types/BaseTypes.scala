package org.jetbrains.plugins.scala.lang.psi.types

import api.expr.ScThisReference
import com.intellij.psi.{PsiTypeParameter, PsiClass}
import api.statements.params.ScTypeParam
import api.toplevel.typedef.ScTypeDefinition
import resolve.ScalaResolveResult
import _root_.scala.collection.mutable.{Set, HashMap, MultiMap}

object BaseTypes {
  def get(t : ScType) : Seq[ScType] = t match {
    case classT@ScDesignatorType(td : ScTypeDefinition) => Seq.single(classT) ++ reduce(td.superTypes)
    case classT@ScDesignatorType(c : PsiClass) => Seq.single(classT) ++ reduce(c.getSuperTypes.map{ScType.create(_, c.getProject)})
    case ScTypeAliasType(poly, s) => get(s.subst(poly.upperBound))
    case ScTypeVariable(_, _, _, upper) => get(upper)
    case p : ScParameterizedType => p.designated match {
      case td : ScTypeDefinition => td.superTypes.map {p.substitutor.subst _}
      case clazz: PsiClass => {
        val s = p.substitutor
        clazz.getSuperTypes.map {t => s.subst(ScType.create(t, clazz.getProject))}
      }
      case _ => Seq.empty
    }
    case sin : ScSingletonType => get(sin.pathType)
    case ScExistentialType(q, wilds) => get(q).map{bt => ScExistentialTypeReducer.reduce(bt, wilds)}
    case ScCompoundType(comps, _, _) => reduce(comps)
    case proj@ScProjectionType(p, name) => proj.resolveResult match {
      case Some(ScalaResolveResult(td : ScTypeDefinition, s)) => td.superTypes.map{s.subst _}
      case Some(ScalaResolveResult(c : PsiClass, s)) =>
        c.getSuperTypes.map{st => s.subst(ScType.create(st, c.getProject))}
      case _ => Seq.empty
    }
  }

  def reduce (types : Seq[ScType]) : Seq[ScType] = {
    def extractClass (t : ScType) = t match {
      case ScDesignatorType(c : PsiClass) => Some(c)
      case ScParameterizedType(ScDesignatorType(c : PsiClass), _) => Some(c)
      case p : ScProjectionType => p.element match {
        case Some(c : PsiClass) => Some(c)
        case _ => None
      }
      case _ => None
    }

    val res = new HashMap[PsiClass, ScType]
    object all extends HashMap[PsiClass, Set[ScType]] with MultiMap[PsiClass, ScType]
    for (t <- types) {
      extractClass(t) match {
        case Some(c) => {
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => ts.find(t1 => !Conformance.conforms(t1, t)) == None
          }
          if (isBest) res += ((c, t))
          all.add(c, t)
        }
        case None => //not a class type
      }
    }
    res.values.toList
  }
}
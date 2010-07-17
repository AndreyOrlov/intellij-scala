package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._

import _root_.scala.collection.Set
import collection.mutable.HashSet
import psi.api.base.patterns.{ScPattern, ScBindingPattern}

import psi.api.toplevel.typedef.ScTypeDefinition
import psi.ScalaPsiUtil
import psi.types.{ScType, PhysicalSignature, Signature, ScSubstitutor}
import caches.CachesUtil
import psi.implicits.ScImplicitlyConvertible


class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          val collectImplicits: Boolean = false,
                          forName: Option[String] = None) extends BaseProcessor(kinds) {
  
  private val signatures = new HashSet[Signature]
  private val names = new HashSet[String]

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    forName match {
      case Some(name) if element.isInstanceOf[PsiNamedElement] && element.asInstanceOf[PsiNamedElement].getName != name => return true
      case _ =>
    }
    lazy val substitutor: ScSubstitutor = {
      state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case x => x
      }
    }

    lazy val isRenamed: Option[String] = {
      state.get(ResolverEnv.nameKey) match {
        case null => None
        case x: String => Some(x)
      }
    }

    lazy val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    lazy val implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION) match {
      case null => None
      case x => Some(x)
    }
    lazy val implType: Option[ScType] = state.get(CachesUtil.IMPLICIT_TYPE) match {
      case null => None
      case x => Some(x)
    }
    lazy val isNamedParameter: Boolean = state.get(CachesUtil.NAMED_PARAM_KEY) match {
      case null => false
      case v => v.booleanValue
    }

    element match {
      case td: ScTypeDefinition if !names.contains(td.getName) => {
        if (kindMatches(td)) candidatesSet += new ScalaResolveResult(td, substitutor, nameShadow = isRenamed,
          implicitFunction = implFunction)
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) if kindMatches(td)=> candidatesSet += new ScalaResolveResult(td, substitutor,
            nameShadow = isRenamed, implicitFunction = implFunction)
          case _ =>
        }
      }
      case named: PsiNamedElement => {
        if (kindMatches(element)) {
          element match {
            case method: PsiMethod => {
              val sign = new PhysicalSignature(method, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter)
              }
            }
            case bindingPattern: ScBindingPattern => {
              val sign = new Signature(isRenamed.getOrElse(bindingPattern.getName), Stream.empty, 0, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter)
              }
            }
            case _ => {
              if (!names.contains(named.getName)) {
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter)
                names += isRenamed.getOrElse(named.getName)
              }
            }
          }
        }
      }
      case pat : ScPattern => for (b <- pat.bindings) execute(b, state)
      case _ => // Is it really a case?
    }
    return true
  }
}

package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates


import _root_.scala.collection.mutable.ListBuffer
import api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import api.expr.ScNewTemplateDefinition
import api.toplevel.{ScEarlyDefinitions}
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.{JavaPsiFacade, PsiElement, PsiClass}
import com.intellij.util.{ArrayFactory}
import parser.ScalaElementTypes
import api.toplevel.templates._
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer
import result.{TypingContext, Success}
import stubs.{ScExtendsBlockStub}
import api.toplevel.typedef.{ScMember, ScTypeDefinition, ScObject}
import collection.Seq
import util.CommonClassesSearcher

/**
 * @author AlexanderPodkhalyuzin
 * Date: 20.02.2008
 */

class ScExtendsBlockImpl extends ScalaStubBasedElementImpl[ScExtendsBlock] with ScExtendsBlock {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScExtendsBlockStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.TEMPLATE_BODY, new ArrayFactory[ScTemplateBody] {
        def create(count: Int): Array[ScTemplateBody] = new Array[ScTemplateBody](count)
      })
      if (array.length == 0) {
        return None
      } else {
        return Some(array.apply(0))
      }
    } else {
      getLastChild match {
        case tb: ScTemplateBody => Some(tb)
        case _ => None
      }
    }
  }

  def empty = getNode.getFirstChildNode == null

  def selfType = (wrap(selfTypeElement) flatMap {
    ste => wrap(ste.typeElement) flatMap {
      te => te.getType(TypingContext.empty)
    }
  }) match {
    case Success(t, _) => Some(t)
    case _ => None
  }

  @volatile
  private var superTypesCache: List[ScType] = null

  @volatile
  private var superTypesModCount: Long = 0L

  def superTypes: List[ScType] = {
    var tp = superTypesCache
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && curModCount == superTypesModCount) {
      return tp
    }
    tp = superTypesInner
    superTypesCache = tp
    superTypesModCount = curModCount
    return tp
  }

  private def superTypesInner: List[ScType] = {
    val buffer = new ListBuffer[ScType]
    def addType(t: ScType): Unit = t match {
      case ScCompoundType(comps, _, _, _) => comps.foreach{addType _}
      case _ => buffer += t
    }
    templateParents match {
      case None => getParentByStub match {
        case obj: ScObject => buffer += AnyRef
        case _ => {
          val so = scalaObject()
          if (so != null) buffer += so
          if (isUnderCaseClass) {
            val prod = scalaProduct()
            if (prod != null) buffer += prod
          }
        }
      }
      case Some(parents: ScTemplateParents) => {
        val parentSupers: Seq[ScType] = parents.superTypes
        val noInferValueType = getParent.isInstanceOf[ScNewTemplateDefinition] && parentSupers.length == 1
        parentSupers foreach {t => addType(if (noInferValueType) t else t.inferValueType)}
      }
    }
    buffer.toList
  }

  private def scalaObject(): ScType = {
    val so = CommonClassesSearcher.getCachedClass(getManager, getResolveScope, "scala.ScalaObject")
    if (so.length > 0) new ScDesignatorType(so(0)) else null
  }

  private def scalaProduct(): ScType = {
    val so = CommonClassesSearcher.getCachedClass(getManager, getResolveScope, "scala.Product")
    if (so.length > 0) new ScDesignatorType(so(0)) else null
  }

  def isAnonymousClass: Boolean = {
    getParent match {
      case _: ScNewTemplateDefinition =>
      case _ => return false
    }
    templateBody match {
      case Some(x) => return true
      case None => return false
    }
  }

  def supers() = {
    val buf = new ArrayBuffer[PsiClass]
    for (t <- superTypes) {
      ScType.extractClass(t) match {
        case Some(c) => buf += c
        case None =>
      }
    }

    buf.toArray[PsiClass]
  }

  def directSupersNames: Seq[String] = {
    templateParents match {
      case None => Seq.empty
      case Some(parents) => {
        val res = new ArrayBuffer[String]
        val pars = parents.typeElements
        for (par <- pars) {
          par match {
            case s: ScSimpleTypeElement =>
              s.reference match {
                case Some(ref) => res += ref.refName
                case _ =>
              }
            case x: ScParameterizedTypeElement =>
              x.typeElement match {
                case s: ScSimpleTypeElement =>
                  s.reference match {
                    case Some(ref) => res += ref.refName
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
        }
        res += "Object"
        res += "ScalaObject"
        if (isUnderCaseClass) res += "Product"
        res.toSeq
      }
    }
  }

  def members() = {
    val bodyMembers: Seq[ScMember] = templateBody match {
      case None => Seq.empty
      case Some(body: ScTemplateBody) => body.members
    }
    val earlyMembers = earlyDefinitions match {
      case None => Seq.empty
      case Some(earlyDefs) => earlyDefs.members
    }

    bodyMembers ++ earlyMembers
  }

  def typeDefinitions = templateBody match {
    case None => Seq.empty
    case Some(body) => body.typeDefinitions
  }

  def nameId() = null

  def aliases() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.aliases
  }

  def functions() = templateBody match {
    case None => Seq.empty
    case Some(body) => body.functions
  }

  def selfTypeElement = templateBody flatMap {body => body.selfTypeElement}

  def templateParents: Option[ScTemplateParents] = {
    val stub = getStub
    if (stub != null) {

      val array = stub.getChildrenByType(TokenSets.TEMPLATE_PARENTS, new ArrayFactory[ScTemplateParents] {
        def create(count: Int): Array[ScTemplateParents] = new Array[ScTemplateParents](count)
      })
      if (array.length == 0) None
      else Some(array.apply(0))
    } else findChild(classOf[ScTemplateParents])
  }

  def earlyDefinitions: Option[ScEarlyDefinitions] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.EARLY_DEFINITIONS, new ArrayFactory[ScEarlyDefinitions] {
        def create(count: Int): Array[ScEarlyDefinitions] = new Array[ScEarlyDefinitions](count)
      })
      if (array.length == 0) None
      else Some(array.apply(0))
    } else findChild(classOf[ScEarlyDefinitions])
  }

  def isUnderCaseClass: Boolean = getParentByStub match {
    case td: ScTypeDefinition if td.isCase => true
    case _ => false
  }

  override def getParent(): PsiElement = {
    val p = super.getParent
    p match {
      case _: ScTypeDefinition => return p
      case _ => return SharedImplUtil.getParent(getNode)
    }
  }
}
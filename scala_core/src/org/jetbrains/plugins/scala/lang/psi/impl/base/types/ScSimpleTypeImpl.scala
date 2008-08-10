package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import tree.{IElementType, TokenSet}
import api.base.types._
import api.base.ScReferenceElement
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import scala.lang.resolve.ScalaResolveResult
import psi.types._
import api.toplevel.ScPolymorphicElement
import api.statements.ScTypeAlias
import psi.impl.toplevel.synthetic.ScSyntheticClass

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {

  override def toString: String = "SimpleTypeElement"

  def singleton = node.findChildByType(ScalaTokenTypes.kTYPE) != null

  override def getType() = {
    if (singleton) new ScSingletonType(pathElement) else reference match {
      case Some(ref) => ref.qualifier match {
        case None => ref.bind match {
          case None => Nothing
          case Some(ScalaResolveResult(e, s)) => e match {
            case alias: ScTypeAlias =>
              new ScTypeAliasType(alias.name, alias.typeParameters.map{ScalaPsiManager.typeVariable(_)}.toList,
                s.subst(alias.lowerBound),
                s.subst(alias.upperBound))
            case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
            case synth: ScSyntheticClass => synth.t
            case _ => new ScDesignatorType(e)
          }
        }
        case Some(q) => new ScProjectionType(new ScSingletonType(q), ref.refName)
      }
      case None => Nothing
    }
  }
}

class F[T]
abstract class A{
  type t

  def r() = {
    var tr : F[t] = new F[t]
    var tr1 : F[t] = new F[t]
    tr = tr1
  }
}
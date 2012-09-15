package org.jetbrains.plugins.scala
package lang
package completion
package filters.modifiers

import com.intellij.lang.ASTNode
import psi.api.ScalaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class ModifiersFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    if (element.isInstanceOf[PsiIdentifier]) return false
    var leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      leaf.getContainingFile match {
        case scalaFile: ScalaFile if scalaFile.isScriptFile() => leaf = leaf.getParent
        case _ =>
      }
    }
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case  _: ScClassParameter => {
          return true
        }
        case _ =>
      }
      val tuple = ScalaCompletionUtil.getForAll(parent,leaf)
      if (tuple._1) return tuple._2
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "modifiers keyword filter"
}
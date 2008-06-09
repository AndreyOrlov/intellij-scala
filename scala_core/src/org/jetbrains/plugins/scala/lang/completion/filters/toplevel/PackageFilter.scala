package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/**
* @author Alexander Podkhalyuzin
* Date: 21.05.2008
*/

class PackageFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent();
      if (parent.isInstanceOf[ScalaFile]) {
        if (leaf.getNextSibling != null && leaf.getNextSibling().getNextSibling().isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1) return false
        else {
          var node = leaf.getPrevSibling
          if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
          node match {
            case _: ScPackageStatement => return false
            case x: PsiErrorElement => {
              val s = ErrMsg("wrong.top.statment.declaration")
              x.getErrorDescription match {
                case `s` => return  true
                case _ => return  false
              }
            }
            case _ => return  true
          }
        }
      }
    }
    return false;
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "'package' keyword filter";
  }
}
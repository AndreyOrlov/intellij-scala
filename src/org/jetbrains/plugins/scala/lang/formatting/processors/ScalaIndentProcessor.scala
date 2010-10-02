package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.formatting._
import psi.api.ScalaFile
import scaladoc.lexer.ScalaDocTokenType
import scaladoc.psi.api.ScDocComment
import settings.ScalaCodeStyleSettings
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._



object ScalaIndentProcessor extends ScalaTokenTypes {
  def getChildIndent(parent: ScalaBlock, child: ASTNode): Indent = {
    val settings = parent.getSettings
    val scalaSettings: ScalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val node = parent.getNode
    if (child.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS ||
                child.getElementType == ScalaDocTokenType.DOC_COMMENT_END) return Indent.getSpaceIndent(1)

    node.getPsi match {
      case expr: ScFunctionExpr => {
        expr.result match {
          case Some(e) if e == child.getPsi => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      }
      case el: ScXmlElement => {
        child.getPsi match {
          case _: ScXmlStartTag | _: ScXmlEndTag => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      }
      case _: ScalaFile => Indent.getNoneIndent
      case _: ScPackaging => Indent.getNoneIndent
      case _: ScMatchStmt => {
        child.getPsi match {
          case _: ScCaseClauses if settings.INDENT_CASE_FROM_SWITCH => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      }
      case _: ScTryBlock | _: ScCatchBlock => {
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.kCATCH |
                  ScalaTokenTypes.tRBRACE | ScalaTokenTypes.kTRY => {
            Indent.getNoneIndent
          }
          case _ => Indent.getNormalIndent
        }
      }
      case _: ScTemplateBody | _: ScRefinement | _: ScExistentialClause | _: ScBlockExpr => {
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE => {
            Indent.getNoneIndent
          }
          case _ => Indent.getNormalIndent
        }
      }
      case _: ScTryStmt => Indent.getNoneIndent
      case _: ScIfStmt | _: ScWhileStmt | _: ScDoStmt | _: ScForStatement
              | _: ScFinallyBlock | _: ScCatchBlock | _: ScFunction => {
        child.getPsi match {
          case _: ScBlockExpr => Indent.getNoneIndent
          case _: ScExpression => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      }
      case _: ScCaseClause => {
        child.getElementType match {
          case ScalaTokenTypes.kCASE | ScalaTokenTypes.tFUNTYPE => Indent.getNoneIndent
          case _ =>
            child.getPsi match {
              case _: ScPattern => Indent.getNoneIndent
              case _ => Indent.getNormalIndent
            }
        }
      }
      case _: ScBlock => Indent.getNoneIndent
      case _: ScEnumerators => Indent.getNormalIndent
      case _: ScExtendsBlock if child.getElementType != ScalaElementTypes.TEMPLATE_BODY => {
        Indent.getContinuationIndent
      }
      case _: ScParameterClause if  scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS => {
        if (child.getElementType == ScalaTokenTypes.tRPARENTHESIS) return Indent.getNoneIndent
        else return Indent.getNormalIndent
      }
      case _: ScParameters | _: ScParameterClause | _: ScPattern | _: ScTemplateParents |
              _: ScExpression | _: ScTypeElement | _: ScTypes | _: ScTypeArgs => {
        Indent.getContinuationWithoutFirstIndent
      }
      case _: ScArgumentExprList => Indent.getNormalIndent
      case _: ScDocComment => {
        Indent.getNoneIndent
      }
      case _ => {
        node.getElementType match {
          case ScalaTokenTypes.kIF | ScalaTokenTypes.kELSE => {
            child.getPsi match {
              case _: ScExpression => Indent.getNormalIndent
              case _ => Indent.getNoneIndent
            }
          }
          case _ => Indent.getNoneIndent
        }
      }
    }
  }
}
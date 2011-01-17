package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 * Date: 03.03.2008
 */

/*
 * Expr ::= (Bindings | [‘implicit’] id | ‘_’) ‘=>’ Expr
 *         | Expr1
 *
 * implicit closures are actually implemented in other parts of the parser, not here! The grammar
 * from the Scala Reference does not match the implementation in Parsers.scala.
 */
object Expr {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER => {
        val pmarker = builder.mark
        builder.advanceLexer //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tFUNTYPE => {
            val psm = pmarker.precede // 'parameter clause'
            val pssm = psm.precede // 'parameter list'
            pmarker.done(ScalaElementTypes.PARAM)
            psm.done(ScalaElementTypes.PARAM_CLAUSE)
            pssm.done(ScalaElementTypes.PARAM_CLAUSES)

            builder.advanceLexer //Ate =>
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
            return true
          }
          case _ => {
            pmarker.drop
            exprMarker.rollbackTo
          }
        }
      }
      
      case ScalaTokenTypes.tLPARENTHESIS => {
        if (Bindings.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tFUNTYPE => {
              builder.advanceLexer //Ate =>
              if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
              exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
              return true
            }
            case _ => exprMarker.rollbackTo
          }
        }
        else {
          exprMarker.drop
        }
      }
      case _ => exprMarker.drop
    }
    return Expr1.parse(builder)
  }
}
package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import patterns.CaseClauses
import builder.ScalaPsiBuilder
import util.ParserUtils
import types.Path

/**
* @author Alexander Podkhalyuzin
* Date: 03.03.2008
*/

/*
 * Expr1 ::= 'if' '(' Expr ')' {nl} Expr [[semi] else Expr]
 *         | 'while' '(' Expr ')' {nl} Expr
 *         | 'try' '{' Block '}' [catch CatchHandler] ['finally' Expr ]
 *         | 'do' Expr [semi] 'while' '(' Expr ')'
 *         | 'for' ('(' Enumerators ')' | '{' Enumerators '}') {nl} ['yield'] Expr
 *         | 'throw' Expr
 * 
 *         | implicit Id => Expr  # Not in Scala Specification yet!
 *
 *         | 'return' [Expr]
 *         | [SimpleExpr '.'] id '=' Expr
 *         | SimpleExpr1 ArgumentExprs '=' Expr
 *         | PostfixExpr
 *         | PostfixExpr Ascription
 *         | PostfixExpr 'match' '{' CaseClauses '}'
 *
 * CatchHandler ::= '{' CaseClauses '}'
 *                | '{' Path '}'
 *                | '(' Path ')'
 *                | Path
 */
object Expr1 {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val exprMarker = builder.mark
    builder.getTokenType match {
    //----------------------if statement------------------------//
      case ScalaTokenTypes.kIF => {
        builder.advanceLexer //Ate if
        builder.getTokenType match {
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            builder.disableNewlines
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ErrMsg("rparenthesis.expected")
              }
            }
            builder.restoreNewlinesState
          }
          case _ => {
            builder error ErrMsg("condition.expected")
          }
        }
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        val rollbackMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer //Ate semi
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kELSE => {
            builder.advanceLexer
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            rollbackMarker.drop
          }
          case _ => {
            rollbackMarker.rollbackTo
          }
        }
        exprMarker.done(ScalaElementTypes.IF_STMT)
        return true
      }
      //--------------------while statement-----------------------//
      case ScalaTokenTypes.kWHILE => {
        builder.advanceLexer //Ate while
        builder.getTokenType match {
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            builder.disableNewlines
            if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
              }
              case _ => {
                builder error ErrMsg("rparenthesis.expected")
              }
            }
            builder.restoreNewlinesState
          }
          case _ => {
            builder error ErrMsg("condition.expected")
          }
        }
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementTypes.WHILE_STMT)
        return true
      }
      //---------------------try statement------------------------//
      case ScalaTokenTypes.kTRY => {
        val tryMarker = builder.mark
        builder.advanceLexer //Ate try
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            builder.advanceLexer //Ate {
            builder.enableNewlines
            def foo() {
              if (!Block.parse(builder, false)) {
                builder error ErrMsg("block.expected")
              }
            }
            ParserUtils.parseLoopUntilRBrace(builder, foo _)
            builder.restoreNewlinesState
          }
          case _ => {
            if (!Block.parse(builder, false)) {
              builder error ErrMsg("block.expected")
            }
          }
        }
        tryMarker.done(ScalaElementTypes.TRY_BLOCK)
        val catchMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kCATCH => {
            builder.advanceLexer //Ate catch
            builder.getTokenType match {
              case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tLPARENTHESIS => {
                val openToken = builder.getTokenType
                builder.advanceLexer //Ate }
                val marker = builder.mark
                val isRef = Path parse (builder, ScalaElementTypes.REFERENCE_EXPRESSION)
                (openToken, isRef) match {
                  case (ScalaTokenTypes.tLPARENTHESIS, true) =>
                    marker.drop
                    builder.getTokenType match {
                      case ScalaTokenTypes.tRPARENTHESIS =>
                        builder.advanceLexer
                      case _ =>
                        builder error ErrMsg("rparenthesis.expected")
                    }

                  case (ScalaTokenTypes.tLBRACE, true) =>
                    marker.drop
                    builder.getTokenType match {
                      case ScalaTokenTypes.tRBRACE =>
                        builder.advanceLexer
                      case _ =>
                        builder error ErrMsg("rbrace.expected")
                    }
                  case (ScalaTokenTypes.tLPARENTHESIS, false) =>
                    marker.drop
                    builder error ErrMsg("wrong.qual.identifier")
                  case (ScalaTokenTypes.tLBRACE, false) =>
                    marker.rollbackTo
                    builder.enableNewlines
                    def foo() {
                      if (!CaseClauses.parse(builder)) {
                        builder error ErrMsg("case.clauses.or.qualified.reference.expected")
                      }
                    }
                    ParserUtils.parseLoopUntilRBrace(builder, foo _)
                    builder.restoreNewlinesState
                }
              }
              case _ => {
                if (!Path.parse(builder, ScalaElementTypes.REFERENCE_EXPRESSION)) {
                  builder error ErrMsg("case.clauses.or.qualified.reference.expected")
                }
              }
            }
            catchMarker.done(ScalaElementTypes.CATCH_BLOCK)
          }
          case _ => {
            catchMarker.drop
          }
        }
        val finallyMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.kFINALLY => {
            builder.advanceLexer //Ate finally
            if (!Expr.parse(builder)) {
              builder error ErrMsg("wrong.expression")
            }
            finallyMarker.done(ScalaElementTypes.FINALLY_BLOCK)
          }
          case _ => {
            finallyMarker.drop
          }
        }
        exprMarker.done(ScalaElementTypes.TRY_STMT)
        return true
      }
      //----------------do statement----------------//
      case ScalaTokenTypes.kDO => {
        builder.advanceLexer //Ate do
        if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer //Ate semi
          }
          case _ => {}
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kWHILE => {
            builder.advanceLexer //Ate while
            builder.getTokenType match {
              case ScalaTokenTypes.tLPARENTHESIS => {
                builder.advanceLexer //Ate (
                builder.disableNewlines
                if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
                builder.getTokenType match {
                  case ScalaTokenTypes.tRPARENTHESIS => {
                    builder.advanceLexer //Ate )
                  }
                  case _ => {
                    builder error ErrMsg("rparenthesis.expected")
                  }
                }
                builder.restoreNewlinesState
              }
              case _ => {
                builder error ErrMsg("condition.expected")
              }
            }
          }
          case _ => {
            builder error ErrMsg("while.expected")
          }
        }
        exprMarker.done(ScalaElementTypes.DO_STMT)
        return true
      }
      //----------------for statement------------------------//
      case ScalaTokenTypes.kFOR => {
        builder.advanceLexer //Ate for
        builder.getTokenType match {
          case ScalaTokenTypes.tLBRACE => {
            builder.advanceLexer //Ate {
            builder.enableNewlines
            def foo() {
              if (!Enumerators.parse(builder)) {
                builder error ErrMsg("enumerators.expected")
              }
            }
            ParserUtils.parseLoopUntilRBrace(builder, foo _)
            builder.restoreNewlinesState
          }
          case ScalaTokenTypes.tLPARENTHESIS => {
            builder.advanceLexer //Ate (
            builder.disableNewlines
            if (!Enumerators.parse(builder)) {
              builder error ErrMsg("enumerators.expected")
            }
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => builder.advanceLexer
              case _ => builder error ErrMsg("rparenthesis.expected")
            }
            builder.restoreNewlinesState
          }
          case _ => {
            builder error ErrMsg("enumerators.expected")
          }
        }
        if (builder.countNewlineBeforeCurrentToken > 1) {
          builder.error(ErrMsg("wrong.expression"))
          exprMarker.done(ScalaElementTypes.FOR_STMT)
          return true
        }
        builder.getTokenType match {
          case ScalaTokenTypes.kYIELD => {
            builder.advanceLexer //Ate yield
          }
          case _ => {}
        }
        if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
        exprMarker.done(ScalaElementTypes.FOR_STMT)
        return true
      }
      //----------------throw statment--------------//
      case ScalaTokenTypes.kTHROW => {
        builder.advanceLexer //Ate throw
        if (!Expr.parse(builder)) {
          builder error ErrMsg("wrong.expression")
        }
        exprMarker.done(ScalaElementTypes.THROW_STMT)
        return true
      }
      //--------------implicit closure--------------//
      case ScalaTokenTypes.kIMPLICIT => {
        val ipmarker = builder.mark
        builder.advanceLexer //Ate implicit
        builder.getTokenType match {
          case ScalaTokenTypes.tIDENTIFIER => {
            val pmarker = builder.mark
            builder.advanceLexer //Ate id
            builder.getTokenType match {
              case ScalaTokenTypes.tFUNTYPE => {
                pmarker.done(ScalaElementTypes.PARAM)
                ipmarker.done(ScalaElementTypes.PARAM_CLAUSE)
                ipmarker.precede.done(ScalaElementTypes.PARAM_CLAUSES)

                builder.advanceLexer //Ate =>
                if (!Expr.parse(builder)) builder error ErrMsg("wrong.expression")
                exprMarker.done(ScalaElementTypes.FUNCTION_EXPR)
                return true
              }
              case _ => {
                pmarker.drop
                ipmarker.drop
              }
            }
          }
          case _ => {
            ipmarker.drop
          }
        }
      }

      //---------------return statement-----------//
      case ScalaTokenTypes.kRETURN => {
        builder.advanceLexer //Ate return
        if (!builder.newlineBeforeCurrentToken)
          Expr parse builder
        exprMarker.done(ScalaElementTypes.RETURN_STMT)
        return true
      }
      
      //---------other cases--------------//
      case _ => {
        if (!PostfixExpr.parse(builder)) {
          exprMarker.rollbackTo
          return false
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tASSIGN => {
            builder.advanceLexer //Ate =
            if (!Expr.parse(builder)) {
              builder error ErrMsg("wrong.expression")
            }
            exprMarker.done(ScalaElementTypes.ASSIGN_STMT)
            return true
          }
          case ScalaTokenTypes.tCOLON => {
            Ascription parse builder
            exprMarker.done(ScalaElementTypes.TYPED_EXPR_STMT)
            return true
          }
          case ScalaTokenTypes.kMATCH => {
            builder.advanceLexer //Ate match
            builder.getTokenType match {
              case ScalaTokenTypes.tLBRACE => {
                builder.advanceLexer //Ate {
                builder.enableNewlines
                def foo() {
                  if (!CaseClauses.parse(builder)) {
                    builder error ErrMsg("case.clauses.expected")
                  }
                }
                ParserUtils.parseLoopUntilRBrace(builder, foo _)
                builder.restoreNewlinesState
              }
              case _ => builder error ErrMsg("case.clauses.expected")
            }
            exprMarker.done(ScalaElementTypes.MATCH_STMT)
            return true
          }
          case _ => {
            exprMarker.drop
            return true
          }
        }
      }
    }
    exprMarker.rollbackTo
    return false
  }
}
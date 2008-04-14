package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle

/**
* @author Alexander Podkhalyuzin
* Date: 15.02.2008
*/

/*
 * SimpleType ::= SimpleType TypeArgs
 *              | SimpleType '#' id
 *              | StableId
 *              | Path '.' 'type'
 *              | '(' Types [','] ')'
 */

object SimpleType {
  def parse(builder: PsiBuilder): Boolean = {

    def parseTale(curMarker: PsiBuilder.Marker) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val newMarker = curMarker.precede
          TypeArgs parse builder
          curMarker.done(ScalaElementTypes.TYPE_GENERIC_CALL)
          parseTale(newMarker)
        }
        case ScalaTokenTypes.tINNER_CLASS => {
          val newMarker = curMarker.precede
          builder.advanceLexer //Ate #
          builder.getTokenType match {
            case ScalaTokenTypes.tIDENTIFIER => {
              builder.advanceLexer //Ate id
            }
            case _ => {
              builder error ScalaBundle.message("identifier.expected",new Array[Object](0))
            }
          }
          curMarker.done(ScalaElementTypes.TYPE_PROJECTION)
          parseTale(newMarker)
        }
        case _ => {
          curMarker.drop
        }
      }
    }

    val simpleMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        val tupleMarker = builder.mark
        builder.advanceLexer
        val (_,isTuple) = Types parse builder
        builder.getTokenType match {
          case ScalaTokenTypes.tCOMMA => {
            builder.advanceLexer //Ate ,
            builder.getTokenType match {
              case ScalaTokenTypes.tRPARENTHESIS => {
                builder.advanceLexer //Ate )
                tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
              }
              case _ => {
                builder error ScalaBundle.message("rparenthesis.expacted", new Array[Object](0))
                tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
              }
            }
          }
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
            if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
          }
          case _ => {
            builder error ScalaBundle.message("rparenthesis.expected", new Array[Object](0))
            if (isTuple) tupleMarker.done(ScalaElementTypes.TUPLE_TYPE)
            else tupleMarker.done(ScalaElementTypes.TYPE_IN_PARENTHESIS)
          }
        }
      }
      case ScalaTokenTypes.kTHIS |
           ScalaTokenTypes.tIDENTIFIER |
           ScalaTokenTypes.kSUPER => {
        val newMarker = builder.mark
        Path parse (builder, ScalaElementTypes.REFERENCE)
        builder.getTokenType match {
          case ScalaTokenTypes.tDOT => {
            builder.advanceLexer //Ate .
            builder.getTokenType match {
              case ScalaTokenTypes.kTYPE => {
                builder.advanceLexer //Ate type
                newMarker.done(ScalaElementTypes.SIMPLE_TYPE)
              }
              case _ => {
                newMarker.rollbackTo
                val fMarker = builder.mark
                StableId parse (builder,ScalaElementTypes.REFERENCE)
                fMarker.done(ScalaElementTypes.SIMPLE_TYPE)
              }
            }
          }
          case _ => {
            newMarker.rollbackTo
            val fMarker = builder.mark
            StableId parse (builder,ScalaElementTypes.REFERENCE)
            fMarker.done(ScalaElementTypes.SIMPLE_TYPE)
          }
        }
      }
      case _ => {
        simpleMarker.rollbackTo
        return false
      }
    }
    parseTale(simpleMarker)
    return true
  }
}
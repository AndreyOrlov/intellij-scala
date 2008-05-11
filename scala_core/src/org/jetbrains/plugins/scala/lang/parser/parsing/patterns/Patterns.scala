package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.bnf._
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

object Patterns {
  def parse(builder: PsiBuilder): Boolean = parse(builder,false)
  def parse(builder: PsiBuilder, underParams: Boolean): Boolean = {
    val patternsMarker = builder.mark
    if (!Pattern.parse(builder)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tUNDER => {
          builder.advanceLexer()
          builder.getTokenText match {
            case "*" => {
              builder.advanceLexer
              patternsMarker.done(ScalaElementTypes.SEQ_WILDCARD)
              return true
            }
            case _ =>
          }
        }
        case _=>
      }
      patternsMarker.rollbackTo
      return false
    }
    builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA => {
        builder.advanceLexer //Ate ,
        var end = false
        while (!end && Pattern.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              builder.advanceLexer //Ate ,
              val roll = builder.mark
              if (ParserUtils.eatSeqWildcardNext(builder)) end = true
            }
            case _ => {
              patternsMarker.done(ScalaElementTypes.PATTERNS)
              return true
            }
          }
        }
        if (underParams) {
          ParserUtils.eatSeqWildcardNext(builder)
        }
        patternsMarker.done(ScalaElementTypes.PATTERNS)
        return true
      }
      case _ => {
        patternsMarker.rollbackTo
        return false
      }
    }
  }
}
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


/** 
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * Pattern2 ::= varid '@' Pattern3
 *            | _ '@' Pattern3
 *            | Pattern3
 */

object Pattern2 {
  def parse(builder: PsiBuilder, forDef: Boolean): Boolean = {

    def isVarId = builder.getTokenText.substring(0, 1).toLowerCase != builder.getTokenText.substring(0, 1)

    def testForId = {
      val m = builder.mark
      builder.advanceLexer
      val s = Set(ScalaTokenTypes.tAT,
      ScalaTokenTypes.tIDENTIFIER,
      ScalaTokenTypes.tDOT,
      ScalaTokenTypes.tLPARENTHESIS)
      val b = !s.contains(builder.getTokenType)
      m.rollbackTo
      b
    }

    val pattern2Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (forDef && testForId) {
          backupMarker.drop
          builder.advanceLexer
          pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
          return true
        } else if (isVarId) {
          backupMarker.rollbackTo
        } else {
          builder.advanceLexer //Ate id
          builder.getTokenType match {
            case ScalaTokenTypes.tAT => {
              builder.advanceLexer //Ate @
              backupMarker.drop
              /*if (ParserUtils.eatSeqWildcardNext(builder)) {
                pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
                return true
              }*/
              if (!Pattern3.parse(builder)) {
                builder error ErrMsg("wrong.pattern")
              }
              pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
              return true
            }
            case _ => {
              backupMarker.rollbackTo
            }
          }
        }
      }
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate id
        builder.getTokenType match {
          case ScalaTokenTypes.tAT => {
            builder.advanceLexer //Ate @
            backupMarker.drop
            if (!Pattern3.parse(builder)) {
              builder error ErrMsg("wrong.pattern")
            }
            pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
            return true
          }
          case _ => {
            backupMarker.rollbackTo
          }
        }
      }
      case _ => {
        backupMarker.drop
      }
    }
    pattern2Marker.drop
    if (Pattern3.parse(builder)) return true
    else return false
  }
}
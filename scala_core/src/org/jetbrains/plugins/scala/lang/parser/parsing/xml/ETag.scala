package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import com.intellij.psi.xml.XmlTokenType

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * ETag ::= </ Name [S] >
 */

object ETag {
  def parse(builder: PsiBuilder): Boolean = {
    val tagMarker = builder.mark()
    builder.getTokenType match {
      case XmlTokenType.XML_END_TAG_START => {
        builder.advanceLexer()
      }
      case _ => {
        tagMarker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case XmlTokenType.XML_NAME => {
        builder.advanceLexer()
      }
      case _ => builder error ErrMsg("xml.name.expected")
    }
    builder.getTokenType match {
      case XmlTokenType.XML_WHITE_SPACE => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case XmlTokenType.XML_TAG_END => {
        builder.advanceLexer()
      }
      case _ => {
        builder error ErrMsg("xml.tag.end.expected")
      }
    }
    tagMarker.done(ScalaElementTypes.XML_END_TAG)
    return true
  }
}
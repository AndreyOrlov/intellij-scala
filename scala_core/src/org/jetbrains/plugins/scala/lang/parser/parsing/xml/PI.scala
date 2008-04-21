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
 * PI ::= <? name [S charTag] ?>
 */

object PI {
  def parse(builder: PsiBuilder): Boolean = {
    val PIMarker = builder.mark
    builder.getTokenType match {
      case XmlTokenType.XML_PI_START => builder.advanceLexer
      case _ => {
        PIMarker.drop
        return false
      }
    }
    builder.getTokenType match {
      case XmlTokenType.XML_NAME => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.name.expected")
    }
    builder.getTokenType match {
      case XmlTokenType.XML_TAG_CHARACTERS => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case XmlTokenType.XML_PI_END => builder.advanceLexer()
      case _ => builder error ErrMsg("xml.PI.end.expected")
    }
    PIMarker.done(ScalaElementTypes.XML_PI)
    return true
  }
}
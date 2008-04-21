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
 * Attribute ::= S Name Eq AttValue
 */

object Attribute {
  def parse(builder: PsiBuilder): Boolean = {
    val attributeMarker = builder.mark
    builder.getTokenType match {
      case XmlTokenType.XML_NAME => {
        builder.advanceLexer()
      }
      case _ => {
        attributeMarker.drop()
        return false
      }
    }
    builder.getTokenType match {
      case XmlTokenType.XML_EQ => builder.advanceLexer()
      case _ => {
        builder error ErrMsg("xml.eq.expected") //todo: add this error
        attributeMarker.drop() //todo: should be done
        return true
      }
    }
    if (!AttrValue.parse(builder)) builder error ErrMsg("xml.attribute.value.expected") //todo: add this error
    attributeMarker.drop() //todo: should be done
    return true
  }
}
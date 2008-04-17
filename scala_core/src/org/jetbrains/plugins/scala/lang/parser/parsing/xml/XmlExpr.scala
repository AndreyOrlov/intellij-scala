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

/**
* @author Alexander Podkhalyuzin
* Date: 17.04.2008
*/

/*
 * XmlExpr ::= XmlContent {Element}
 */

object XmlExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val xmlMarker = builder.mark
    if (!XmlContent.parse(builder)) {
      xmlMarker.drop
      return false
    }
    while (Element.parse(builder)) {}
    xmlMarker.drop //TODO: should be done
    return true
  }
}
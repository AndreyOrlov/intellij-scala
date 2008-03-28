package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Enumerators ::= Generator {semi Enumerator}
 */

object Enumerators {
  def parse(builder:PsiBuilder): Boolean = {
    val enumsMarker = builder.mark
    if (!Generator.parse(builder)) {
      enumsMarker.drop
      return false
    }
    var exit = true
    while (builder.getTokenType == ScalaTokenTypes.tLINE_TERMINATOR ||
      builder.getTokenType == ScalaTokenTypes.tSEMICOLON && exit) {
      builder.advanceLexer //Ate semi
      if (!Enumerator.parse(builder)) exit = false
    }
    enumsMarker.done(ScalaElementTypes.ENUMERATORS)
    return true
  }
}
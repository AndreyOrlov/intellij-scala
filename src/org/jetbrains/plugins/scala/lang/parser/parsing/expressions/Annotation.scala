package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import builder.ScalaPsiBuilder

/**
 * @author Alexander Podkhalyuzin
 *  Date: 06.03.2008
 */

/*
 * Annmotation ::= '@' AnnotationExpr [nl]
 */

object Annotation {
  def parse(builder: ScalaPsiBuilder, countLinesAfterAnnotation: Boolean = true): Boolean = {
    val rollbackMarker = builder.mark()
    val annotMarker = builder.mark
    builder.getTokenText match {
      case "@" => {
        builder.advanceLexer() //Ate @
      }
      case _ => {
        annotMarker.drop()
        rollbackMarker.drop()
        return false
      }
    }
    if (!AnnotationExpr.parse(builder)) {
      builder error ScalaBundle.message("wrong.annotation.expression")
      annotMarker.drop()
    } else {
      annotMarker.done(ScalaElementTypes.ANNOTATION)
    }
    if (countLinesAfterAnnotation && builder.twoNewlinesBeforeCurrentToken) {
      rollbackMarker.rollbackTo()
      return false
    } else rollbackMarker.drop()
    true
  }
}
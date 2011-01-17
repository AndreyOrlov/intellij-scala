package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import _root_.org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParamClause
import base.AccessModifier
import com.intellij.lang.PsiBuilder
import expressions.Annotation
import lexer.ScalaTokenTypes
import params.ClassParamClauses
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * ClassDef ::= id [TypeParamClause] {Annotation} [AcessModifier] [ClassParamClauses] ClassTemplateOpt
 */

object ClassDef {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => builder.advanceLexer //Ate identifier
      case _ => {
        builder error ErrMsg("identifier.expected")
        return false
      }
    }
    //parsing type parameters
    builder.getTokenType match {
      case ScalaTokenTypes.tLSQBRACKET =>
        TypeParamClause parse builder
      case _ => {/*it could be without type parameters*/}
    }
    val constructorMarker = builder.mark
    val annotationsMarker = builder.mark
    while (Annotation.parse(builder)) {}
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    val modifierMareker = builder.mark
    //parse AccessModifier
    builder.getTokenType match {
      case ScalaTokenTypes.kPRIVATE
         | ScalaTokenTypes.kPROTECTED => {
        AccessModifier parse builder
      }
      case _ => {/*it could be without acces modifier*/}
    }
    modifierMareker.done(ScalaElementTypes.MODIFIERS)
    //parse class parameters clauses
    ClassParamClauses parse builder
    constructorMarker.done(ScalaElementTypes.PRIMARY_CONSTRUCTOR)
    //parse requires block
    builder.getTokenType match {
      case ScalaTokenTypes.kREQUIRES => Requires parse builder
      case _ => {/*it could be without requires block*/}
    }
    //parse extends block
    ClassTemplateOpt parse builder
    return true
  }
}
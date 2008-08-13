package org.jetbrains.plugins.scala.lang.formatting.processors

import psi.api.base._
import scaladoc.psi.api.ScDocComment
import scaladoc.lexer.ScalaDocTokenType
import settings.ScalaCodeStyleSettings
import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import com.intellij.psi.xml._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.formatting.patterns._

import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._;
import com.intellij.formatting.Spacing;

object ScalaSpacingProcessor extends ScalaTokenTypes {


  val NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  val NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  val IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);



  def getSpacing(left: ScalaBlock, right: ScalaBlock): Spacing = {
    val settings = left.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    def getSpacing(x: Int, y: Int, z: Int) = if (scalaSettings.KEEP_LINE_BREAKS)
      Spacing.createSpacing(y, y, z, true, x)
    else
      Spacing.createSpacing(y, y, z, false, 0)
    val WITHOUT_SPACING = getSpacing(scalaSettings.KEEP_BLANK_LINES_IN_CODE, 0, 0)
    val WITH_SPACING = getSpacing(scalaSettings.KEEP_BLANK_LINES_IN_CODE, 1, 0)
    val ON_NEW_LINE = getSpacing(scalaSettings.KEEP_BLANK_LINES_IN_CODE, 0, 1)
    val DOUBLE_LINE = getSpacing(scalaSettings.KEEP_BLANK_LINES_IN_CODE, 0, 2)
    val leftNode = left.getNode
    val rightNode = right.getNode
    val (leftString, rightString) = (left.getTextRange.substring(leftNode.getPsi.getContainingFile.getNode.getText),
            right.getTextRange.substring(leftNode.getPsi.getContainingFile.getNode.getText)) //for debug
    //comments processing
    if (leftNode.getPsi.isInstanceOf[ScDocComment]) return ON_NEW_LINE
    if (rightNode.getPsi.isInstanceOf[ScDocComment]) return DOUBLE_LINE
    if (rightNode.getPsi.isInstanceOf[PsiComment] || leftNode.getPsi.isInstanceOf[PsiComment])
      return COMMON_SPACING
    if (rightNode.getElementType == ScalaDocTokenType.DOC_COMMENT_DATA || leftNode.getElementType == ScalaDocTokenType.DOC_COMMENT_DATA)
      return NO_SPACING_WITH_NEWLINE
    //; : . and , processing
    if (rightNode.getText.length > 0 && rightNode.getText()(0) == '.') {
      if (rightNode.getElementType != ScalaTokenTypes.tFLOAT && !rightNode.getPsi.isInstanceOf[ScLiteral]) return WITHOUT_SPACING
    }
    if (rightNode.getText.length > 0 && rightNode.getText()(0) == ',') {
      if (scalaSettings.SPACE_BEFORE_COMMA) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getElementType == ScalaTokenTypes.tCOLON) {
      var left = leftNode
      // For operations like
      // var Object_!= : Symbol = _
      if (scalaSettings.SPACE_BEFORE_COLON) return WITH_SPACING
      while (left != null && left.getLastChildNode != null) {
        left = left.getLastChildNode
      }
      return if (left.getElementType == ScalaTokenTypes.tIDENTIFIER &&
              !left.getText.matches(".*[A-Za-z0-9]")) WITH_SPACING else WITHOUT_SPACING
    }
    if (rightNode.getText.length > 0 && rightNode.getText()(0) == ';') {
      if (scalaSettings.SPACE_BEFORE_SEMICOLON && !(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }
    if (leftNode.getText.length > 0 && leftNode.getText()(leftNode.getText.length - 1) == '.') {
      return WITHOUT_SPACING
    }
    if (leftNode.getText.length > 0 && leftNode.getText()(leftNode.getText.length - 1) == ',') {
      if (scalaSettings.SPACE_AFTER_COMMA) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getElementType == ScalaTokenTypes.tCOLON) {
      if (scalaSettings.SPACE_AFTER_COLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftNode.getText.length > 0 && leftNode.getText()(leftNode.getText.length - 1) == ';') {
      if (scalaSettings.SPACE_AFTER_SEMICOLON && !(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from right) as Java cases
    if (rightNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      leftNode.getElementType match {
        case ScalaTokenTypes.kIF => {
          if (scalaSettings.SPACE_BEFORE_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case ScalaTokenTypes.kWHILE => {
          if (scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case ScalaTokenTypes.kFOR => {
          if (scalaSettings.SPACE_BEFORE_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScFunction]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getPsi.isInstanceOf[ScArguments] &&
            (leftNode.getTreeParent.getPsi.isInstanceOf[ScMethodCall] ||
                    leftNode.getTreeParent.getPsi.isInstanceOf[ScConstructor])) {
      if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from right) only Scala cases
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScPrimaryConstructor]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
      else return WITHOUT_SPACING
    }
    if (rightNode.getPsi.isInstanceOf[ScPrimaryConstructor] &&
            rightNode.getText.length > 0 &&
            rightNode.getText.substring(0, 1) == "(") {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
      else return WITHOUT_SPACING
    } else if (rightNode.getPsi.isInstanceOf[ScPrimaryConstructor]) {
      return WITH_SPACING
    }
    if (leftNode.getPsi.isInstanceOf[ScParameterClause] &&
            rightNode.getPsi.isInstanceOf[ScParameterClause]) {
      return WITHOUT_SPACING //todo: add setting
    }
    if (rightNode.getPsi.isInstanceOf[ScPatternArgumentList] &&
            rightNode.getTreeParent.getPsi.isInstanceOf[ScConstructorPattern]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING //todo: add setting
      else return WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from left)
    if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS)
        return WITHOUT_SPACING
      leftNode.getTreeParent.getPsi match {
        case _: ScForStatement => {
          if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt | _: ScDoStmt => {
          if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedExpr => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScPatternArgumentList => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScArguments => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedPattern => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScTuplePattern => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScParenthesisedTypeElement => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScTupleTypeElement => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScTuple => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScBindings => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScFunctionalTypeElement => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }
    //processing right parenthesis (if it's from right)
    if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS) {
      if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
        return WITHOUT_SPACING
      rightNode.getTreeParent.getPsi match {
        case _: ScForStatement => {
          if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt | _: ScDoStmt => {
          if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedExpr => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScPatternArgumentList => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScArguments => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedPattern => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScTuplePattern => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScParenthesisedTypeElement => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScTupleTypeElement => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScTuple => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScBindings => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScFunctionalTypeElement => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }

    //proccessing sqbrackets
    if (leftNode.getElementType == ScalaTokenTypes.tLSQBRACKET) {
      if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
        return WITHOUT_SPACING
      }
      else {
        if (scalaSettings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
        else return WITHOUT_SPACING
      }
    }
    if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
      if (scalaSettings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getText.length > 0 &&
            rightNode.getText()(0) == '[') {
      return WITHOUT_SPACING
    }

    //processing before left brace
    if (rightNode.getText.length > 0 && rightNode.getText()(0) == '{') {
      rightNode.getTreeParent.getPsi match {
        case _: ScTypeDefinition => {
          if (scalaSettings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFunction => {
          if (scalaSettings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_BEFORE_IF_LBRACE && !(leftNode.getElementType == ScalaTokenTypes.kELSE)) return WITH_SPACING
          else if (scalaSettings.SPACE_BEFORE_ELSE_LBRACE && leftNode.getElementType == ScalaTokenTypes.kELSE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt => {
          if (scalaSettings.SPACE_BEFORE_WHILE_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScForStatement => {
          if (scalaSettings.SPACE_BEFORE_FOR_LBRACE && leftNode.getElementType != ScalaTokenTypes.kFOR) return WITH_SPACING
          else if (leftNode.getElementType == ScalaTokenTypes.kFOR) return WITHOUT_SPACING //todo: add setting
          else return WITHOUT_SPACING
        }
        case _: ScDoStmt => {
          if (scalaSettings.SPACE_BEFORE_DO_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScMatchStmt => {
          if (scalaSettings.SPACE_BEFORE_MATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTryBlock => {
          if (scalaSettings.SPACE_BEFORE_TRY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScCatchBlock => {
          if (scalaSettings.SPACE_BEFORE_CATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFinallyBlock => {
          if (scalaSettings.SPACE_BEFORE_FINALLY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScExistentialClause => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScAnnotationExpr => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScExtendsBlock => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScPackaging => {
          return WITH_SPACING //todo: add setting
        }
        case _ => {
          return WITH_SPACING
        }
      }
    }


    //todo: processing spasing operators

    //processing right brace
    if (leftNode.getText.length > 0 && leftNode.getText()(leftNode.getText.length - 1) == '}') {
      rightNode.getElementType match {
        case ScalaTokenTypes.kELSE => {
          if (scalaSettings.ELSE_ON_NEW_LINE) return ON_NEW_LINE
          else return WITH_SPACING
        }
        case ScalaTokenTypes.kWHILE => {
          if (scalaSettings.WHILE_ON_NEW_LINE) return ON_NEW_LINE
          else return WITH_SPACING
        }
        case ScalaTokenTypes.kCATCH => {
          if (scalaSettings.CATCH_ON_NEW_LINE) return ON_NEW_LINE
          else return WITH_SPACING
        }
        case ScalaTokenTypes.kFINALLY => {
          if (scalaSettings.FINALLY_ON_NEW_LINE) return ON_NEW_LINE
          else return WITH_SPACING
        }
        case _ =>
      }
    }
    if (rightNode.getElementType == ScalaTokenTypes.tRBRACE) {
      rightNode.getTreeParent.getPsi match {
        case _: ScTemplateBody | _: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                _: ScTryBlock | _: ScCatchBlock => {
          return Spacing.createSpacing(0, 0, 1, true, scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE)
        }
        case _ => return Spacing.createSpacing(0, 0, 0, true, scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE)
      }
    }
    if (leftNode.getElementType == ScalaTokenTypes.tLBRACE) {
      leftNode.getTreeParent.getPsi match {
        case _: ScTemplateBody | _: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                _: ScTryBlock | _: ScCatchBlock => {
          return Spacing.createSpacing(0, 0, 1, true, scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE)
        }
        case _ => return Spacing.createSpacing(0, 0, 0, true, scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE)
      }
    }

    //special else if treatment
    if (leftNode.getElementType == ScalaTokenTypes.kELSE && rightNode.getPsi.isInstanceOf[ScIfStmt]) {
      if (scalaSettings.SPECIAL_ELSE_IF_TREATMENT) {
        return Spacing.createSpacing(1, 1, 0, false, 0)
      } else return ON_NEW_LINE
    }

    //special for "case <caret> =>" (for SurroundWith)
    if (leftNode.getElementType == ScalaTokenTypes.kCASE &&
            rightNode.getElementType == ScalaTokenTypes.tFUNTYPE) return Spacing.createSpacing(2, 2, 0, false, 0)


    (leftNode.getElementType, rightNode.getElementType,
            leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
    //xml
      case (XmlTokenType.XML_START_TAG_START | XmlTokenType.XML_END_TAG_START |
              XmlTokenType.XML_CDATA_START | XmlTokenType.XML_PI_START, _, _, _) => return NO_SPACING
      case (_, XmlTokenType.XML_TAG_END | XmlTokenType.XML_EMPTY_ELEMENT_END |
              XmlTokenType.XML_CDATA_END | XmlTokenType.XML_PI_END, _, _) => return NO_SPACING
      case (XmlTokenType.XML_NAME, ScalaElementTypes.XML_ATTRIBUTE, _, _) => return COMMON_SPACING
      case (XmlTokenType.XML_NAME, XmlTokenType.XML_EQ, _, _) => return NO_SPACING
      case (XmlTokenType.XML_EQ, XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER |
              ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, _, _) => return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, _, _) => return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, _, _) => return NO_SPACING
      case (ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, _, _, _) => return NO_SPACING
      case (_, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _) => return NO_SPACING
      case (_, XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_END
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _) => return NO_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_START
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _, _) => return NO_SPACING
      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementTypes.TYPE_PARAM_CLAUSE, ScalaElementTypes.PRIMARY_CONSTRUCTOR, _, _)
        if !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasAnnotation &&
                !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => return NO_SPACING
      //Type*
      case (_, ScalaTokenTypes.tIDENTIFIER, _, ScalaElementTypes.PARAM_TYPE) if (rightNode.getText == "*") => return NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.PARAM_CLAUSES, _, _) => return NO_SPACING
      case (_, ScalaElementTypes.TYPE_ARGS, _, (ScalaElementTypes.TYPE_GENERIC_CALL | ScalaElementTypes.GENERIC_CALL)) => return NO_SPACING
      case (_, ScalaElementTypes.PATTERN_ARGS, _, ScalaElementTypes.CONSTRUCTOR_PATTERN) => return NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT, _, _, _) => return NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaElementTypes.NAMING_PATTERN, _) => return NO_SPACING
      case (_, ScalaTokenTypes.tAT, _, _) => return NO_SPACING_WITH_NEWLINE
      case (ScalaElementTypes.ANNOTATION, _, _, _) => return COMMON_SPACING
      //Prefix Identifier
      case (ScalaTokenTypes.tIDENTIFIER, _,
      (ScalaElementTypes.LITERAL | ScalaElementTypes.PREFIX_EXPR
              | ScalaElementTypes.VARIANT_TYPE_PARAM), _) => return NO_SPACING
      //Braces
      case (ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING
      case (ScalaTokenTypes.tLBRACE, _,
      (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => return IMPORT_BETWEEN_SPACING
      case (ScalaTokenTypes.tLBRACE, _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tRBRACE, (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => return IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tRBRACE, _, _) => return NO_SPACING_WITH_NEWLINE
      //Semicolon
      case (ScalaTokenTypes.tSEMICOLON, _, _, _) => return IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tSEMICOLON, _, _) => return NO_SPACING
      //Imports
      case (ScalaElementTypes.IMPORT_STMT, ScalaElementTypes.IMPORT_STMT, _, _) => return IMPORT_BETWEEN_SPACING
      case (ScalaElementTypes.IMPORT_STMT, _, _, _) => return IMPORT_OTHER_SPACING
      //Dot
      case (ScalaTokenTypes.tDOT, _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tDOT, _, _) => return NO_SPACING
      //Comma
      case (ScalaTokenTypes.tCOMMA, _, _, _) => return COMMON_SPACING
      case (_, ScalaTokenTypes.tCOMMA, _, _) => return NO_SPACING
      //Parenthesises and Brackets
      case ((ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET), _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tLSQBRACKET, _, _) => return NO_SPACING
      case (_, ScalaTokenTypes.tLPARENTHESIS, ScalaElementTypes.CONSTRUCTOR_PATTERN, _) => return NO_SPACING
      case ((ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _, _) => return COMMON_SPACING
      case (_, (ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _) => return NO_SPACING_WITH_NEWLINE
      //Case clauses
      case (ScalaElementTypes.CASE_CLAUSE, _, _, _) => return IMPORT_BETWEEN_SPACING
      case (_, ScalaElementTypes.CASE_CLAUSE, _, _) => return IMPORT_BETWEEN_SPACING
      //#
      case (ScalaTokenTypes.tINNER_CLASS, _, _, _) => return NO_SPACING
      case (ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER, _, _) => {
        leftNode.getPsi.getNextSibling match {
          case _: PsiWhiteSpace => return COMMON_SPACING
          case _ => return NO_SPACING
        }
      }
      case (_, ScalaTokenTypes.tINNER_CLASS, _, _) => return NO_SPACING
      //Other cases
      case _ => {
        return COMMON_SPACING
      }
    }
  }
}
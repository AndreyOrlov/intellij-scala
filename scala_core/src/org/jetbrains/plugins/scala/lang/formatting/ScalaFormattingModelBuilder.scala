package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.util._
import com.intellij.lang._

sealed class ScalaFormattingModelBuilder extends FormattingModelBuilder {

  def createModel(element: PsiElement, settings: CodeStyleSettings) = {

    val file = element.getContainingFile
    val node = file.getNode
    FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(),
         new ScalaBlock(null, node, null, null, null, null, settings), settings);
  }

  def getRangeAffectingIndent(file: PsiFile, offset: Int, elementAtOffset: ASTNode): TextRange = {
    elementAtOffset.getTextRange
  }

}
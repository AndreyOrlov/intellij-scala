package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiFile;
import com.intellij.application.options.CodeStyleAbstractPanel;

import javax.swing.*;

import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JTabbedPane tabbedPane;
  private JPanel previewPanel;
  private JPanel spacingPanel;
  private JCheckBox beforeCommaBox;
  private JCheckBox afterCommaBox;
  private JCheckBox beforeColonBox;
  private JCheckBox afterColonBox;
  private JCheckBox beforeSemicolonBox;
  private JCheckBox afterSemicolonBox;
  private JCheckBox beforeIfBox;
  private JCheckBox beforeForBox;
  private JCheckBox beforeMethodBox;
  private JCheckBox beforeMethodCallBox;
  private JCheckBox withinForBox;
  private JCheckBox withinIfBox;
  private JCheckBox withinWhileBox;
  private JCheckBox withinMethodBox;
  private JCheckBox withinMethodCallBox;
  private JCheckBox withinBox;
  private JCheckBox withinBracketsBox;
  private JCheckBox beforeClassLBraceBox;
  private JCheckBox beforeMethodLBraceBox;
  private JCheckBox beforeIfLBraceBox;
  private JCheckBox beforeWhileLBraceBox;
  private JCheckBox beforeDoLBraceBox;
  private JCheckBox beforeForLBraceBox;
  private JCheckBox beforeMatchLBrace;
  private JCheckBox beforeTryLBraceBox;
  private JCheckBox beforeCatchLBraceBox;
  private JCheckBox beforeFinallyLBraceBox;
  private JCheckBox beforeElseLBraceBox;
  private JCheckBox beforeWhileBox;
  private JPanel blackLines;
  private JSpinner keepCodeSpinner;
  private JSpinner keepBeforeSpinner;
  private JCheckBox keepLineBreaksCheckBox;
  private JPanel alignmentTab;
  private JCheckBox alignParametersCheckBox;
  private JCheckBox alignListOfIdentifiersCheckBox;
  private JCheckBox alignBinaryOperationsCheckBox;
  private JCheckBox alignParenthesizedExpressionCheckBox;
  private JCheckBox alignExtendsListCheckBox;
  private JCheckBox alignParametersInCallsCheckBox;
  private JCheckBox alignForStatementCheckBox;
  private JCheckBox elseOnNewLineCheckBox;
  private JCheckBox finallyOnNewLineCheckBox;
  private JCheckBox catchOnNewLineCheckBox;
  private JCheckBox whileOnNewLineCheckBox;
  private JCheckBox specialElseIfTreatmentCheckBox;
  private JCheckBox indentCaseFromMatchCheckBox;
  private JSpinner indentSpinner;

  public ScalaCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    setSettings(scalaSettings);
    installPreviewPanel(previewPanel);
  }

  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  protected int getRightMargin() {
    return 0;
  }

  protected void prepareForReformat(PsiFile psiFile) {
  }

  @NotNull
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  protected String getPreviewText() {
    return "package preview.file\n\n" +
            "" +
            "import scala.collection.mutable._\n\n" +
            "" +
            "abstract class R[T](x: Int) extends {val y = x} with R1[T] {\n" +
            "  def foo(z: Int): R1 = new R[Int](z)\n" +
            "  def default = foo(0)\n" +
            "  val x: T\n" +
            "}\n\n" +
            "" +
            "trait R1[T]";
  }

  public void apply(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    scalaSettings.SPACE_AFTER_COLON = afterColonBox.isSelected();
    scalaSettings.SPACE_AFTER_COMMA = afterCommaBox.isSelected();
    scalaSettings.SPACE_AFTER_SEMICOLON = afterSemicolonBox.isSelected();
    scalaSettings.SPACE_BEFORE_CATCH_LBRACE = beforeCatchLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_CLASS_LBRACE = beforeClassLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FINALLY_LBRACE = beforeFinallyLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_COLON = beforeColonBox.isSelected();
    scalaSettings.SPACE_BEFORE_COMMA = beforeCommaBox.isSelected();
    scalaSettings.SPACE_BEFORE_DO_LBRACE = beforeDoLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FOR_LBRACE = beforeForLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_FOR_PARENTHESES = beforeForBox.isSelected();
    scalaSettings.SPACE_BEFORE_IF_LBRACE = beforeIfLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_ELSE_LBRACE = beforeElseLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_IF_PARENTHESES = beforeIfBox.isSelected();
    scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES = beforeWhileBox.isSelected();
    scalaSettings.SPACE_BEFORE_MATCH_LBRACE = beforeMatchLBrace.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = beforeMethodCallBox.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_LBRACE = beforeMethodLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES = beforeMethodBox.isSelected();
    scalaSettings.SPACE_BEFORE_SEMICOLON = beforeSemicolonBox.isSelected();
    scalaSettings.SPACE_BEFORE_TRY_LBRACE = beforeTryLBraceBox.isSelected();
    scalaSettings.SPACE_BEFORE_WHILE_LBRACE = beforeWhileLBraceBox.isSelected();
    scalaSettings.SPACE_WITHIN_BRACKETS = withinBracketsBox.isSelected();
    scalaSettings.SPACE_WITHIN_FOR_PARENTHESES = withinForBox.isSelected();
    scalaSettings.SPACE_WITHIN_IF_PARENTHESES = withinIfBox.isSelected();
    scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = withinMethodCallBox.isSelected();
    scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES = withinMethodBox.isSelected();
    scalaSettings.SPACE_WITHIN_PARENTHESES = withinBox.isSelected();
    scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES = withinWhileBox.isSelected();
    scalaSettings.KEEP_LINE_BREAKS = keepLineBreaksCheckBox.isSelected();
    if ((Integer) keepCodeSpinner.getValue() >= 0) {
      scalaSettings.KEEP_BLANK_LINES_IN_CODE = (Integer) keepCodeSpinner.getValue();
    } else {
      scalaSettings.KEEP_BLANK_LINES_IN_CODE = 0;
      keepCodeSpinner.setValue(0);
    }
    if ((Integer) keepBeforeSpinner.getValue() >= 0) {
      scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = (Integer) keepBeforeSpinner.getValue();
    } else {
      scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
      keepBeforeSpinner.setValue(0);
    }

    scalaSettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = alignListOfIdentifiersCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION = alignBinaryOperationsCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_EXTENDS_LIST = alignExtendsListCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_FOR = alignForStatementCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARAMETERS = alignParametersCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = alignParametersInCallsCheckBox.isSelected();
    scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = alignParenthesizedExpressionCheckBox.isSelected();

    scalaSettings.INDENT_CASE_FROM_SWITCH = indentCaseFromMatchCheckBox.isSelected();
    scalaSettings.SPECIAL_ELSE_IF_TREATMENT = specialElseIfTreatmentCheckBox.isSelected();
    scalaSettings.INDENT = (Integer) indentSpinner.getValue();
    scalaSettings.ELSE_ON_NEW_LINE = elseOnNewLineCheckBox.isSelected();
    scalaSettings.CATCH_ON_NEW_LINE = catchOnNewLineCheckBox.isSelected();
    scalaSettings.FINALLY_ON_NEW_LINE = finallyOnNewLineCheckBox.isSelected();
    scalaSettings.WHILE_ON_NEW_LINE = whileOnNewLineCheckBox.isSelected();
  }

  private boolean getBoxValue(JCheckBox checkBox) {
    return checkBox.isSelected();
  }

  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    if (scalaSettings.SPACE_AFTER_COLON != afterColonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_AFTER_COMMA != afterCommaBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_AFTER_SEMICOLON != afterSemicolonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_CATCH_LBRACE != beforeCatchLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_CLASS_LBRACE != beforeClassLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FINALLY_LBRACE != beforeFinallyLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_COLON != beforeColonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_COMMA != beforeCommaBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_DO_LBRACE != beforeDoLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FOR_LBRACE != beforeForLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_FOR_PARENTHESES != beforeForBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_IF_LBRACE != beforeIfLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_ELSE_LBRACE != beforeElseLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_IF_PARENTHESES != beforeIfBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES != beforeWhileBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_MATCH_LBRACE != beforeMatchLBrace.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES != beforeMethodCallBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_LBRACE != beforeMethodLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES != beforeMethodBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_SEMICOLON != beforeSemicolonBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_TRY_LBRACE != beforeTryLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_BEFORE_WHILE_LBRACE != beforeWhileLBraceBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_BRACKETS != withinBracketsBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES != withinForBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES != withinIfBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES != withinMethodCallBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES != withinMethodBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_PARENTHESES != withinBox.isSelected()) {
      return true;
    }
    if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES != withinWhileBox.isSelected()) {
      return true;
    }
    if (scalaSettings.KEEP_BLANK_LINES_BEFORE_RBRACE != (Integer) keepBeforeSpinner.getValue()) return true;
    if (scalaSettings.KEEP_BLANK_LINES_IN_CODE != (Integer) keepCodeSpinner.getValue()) return true;
    if (scalaSettings.KEEP_LINE_BREAKS != keepLineBreaksCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION != alignListOfIdentifiersCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_BINARY_OPERATION != alignBinaryOperationsCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_EXTENDS_LIST != alignExtendsListCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_FOR != alignForStatementCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARAMETERS != alignParametersCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS != alignParametersInCallsCheckBox.isSelected()) return true;
    if (scalaSettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION != alignParenthesizedExpressionCheckBox.isSelected()) return true;

    if (scalaSettings.ELSE_ON_NEW_LINE != elseOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.WHILE_ON_NEW_LINE != whileOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.CATCH_ON_NEW_LINE != catchOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.FINALLY_ON_NEW_LINE != finallyOnNewLineCheckBox.isSelected()) return true;
    if (scalaSettings.SPECIAL_ELSE_IF_TREATMENT != specialElseIfTreatmentCheckBox.isSelected()) return true;
    if (scalaSettings.INDENT_CASE_FROM_SWITCH != indentCaseFromMatchCheckBox.isSelected()) return true;
    if (scalaSettings.INDENT != (Integer) indentSpinner.getValue()) return true;
    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);
    setSettings(scalaSettings);
  }

  private void setSettings(ScalaCodeStyleSettings settings) {
    //set spacing settings
    setValue(afterColonBox, settings.SPACE_AFTER_COLON);
    setValue(beforeColonBox, settings.SPACE_BEFORE_COLON);
    setValue(afterCommaBox, settings.SPACE_AFTER_COMMA);
    setValue(beforeCommaBox, settings.SPACE_BEFORE_COMMA);
    setValue(afterSemicolonBox, settings.SPACE_AFTER_SEMICOLON);
    setValue(beforeSemicolonBox, settings.SPACE_BEFORE_SEMICOLON);
    setValue(beforeIfBox, settings.SPACE_BEFORE_IF_PARENTHESES);
    setValue(beforeWhileBox, settings.SPACE_BEFORE_WHILE_PARENTHESES);
    setValue(beforeForBox, settings.SPACE_BEFORE_FOR_PARENTHESES);
    setValue(beforeMethodBox, settings.SPACE_BEFORE_METHOD_PARENTHESES);
    setValue(beforeMethodCallBox, settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    setValue(withinForBox, settings.SPACE_WITHIN_FOR_PARENTHESES);
    setValue(withinIfBox, settings.SPACE_WITHIN_IF_PARENTHESES);
    setValue(withinWhileBox, settings.SPACE_WITHIN_WHILE_PARENTHESES);
    setValue(withinBox, settings.SPACE_WITHIN_PARENTHESES);
    setValue(withinMethodBox, settings.SPACE_WITHIN_METHOD_PARENTHESES);
    setValue(withinMethodCallBox, settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    setValue(withinBracketsBox, settings.SPACE_WITHIN_BRACKETS);
    setValue(beforeClassLBraceBox, settings.SPACE_BEFORE_CLASS_LBRACE);
    setValue(beforeMethodLBraceBox, settings.SPACE_BEFORE_METHOD_LBRACE);
    setValue(beforeIfLBraceBox, settings.SPACE_BEFORE_IF_LBRACE);
    setValue(beforeElseLBraceBox, settings.SPACE_BEFORE_IF_LBRACE);
    setValue(beforeWhileLBraceBox, settings.SPACE_BEFORE_WHILE_LBRACE);
    setValue(beforeDoLBraceBox, settings.SPACE_BEFORE_DO_LBRACE);
    setValue(beforeForLBraceBox, settings.SPACE_BEFORE_FOR_LBRACE);
    setValue(beforeMatchLBrace, settings.SPACE_BEFORE_MATCH_LBRACE);
    setValue(beforeTryLBraceBox, settings.SPACE_BEFORE_TRY_LBRACE);
    setValue(beforeCatchLBraceBox, settings.SPACE_BEFORE_CATCH_LBRACE);
    setValue(beforeFinallyLBraceBox, settings.SPACE_BEFORE_FINALLY_LBRACE);

    setValue(keepLineBreaksCheckBox, settings.KEEP_LINE_BREAKS);
    setValue(keepBeforeSpinner, settings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    setValue(keepCodeSpinner, settings.KEEP_BLANK_LINES_IN_CODE);

    setValue(alignListOfIdentifiersCheckBox, settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);
    setValue(alignBinaryOperationsCheckBox, settings.ALIGN_MULTILINE_BINARY_OPERATION);
    setValue(alignExtendsListCheckBox, settings.ALIGN_MULTILINE_EXTENDS_LIST);
    setValue(alignForStatementCheckBox, settings.ALIGN_MULTILINE_FOR);
    setValue(alignParametersCheckBox, settings.ALIGN_MULTILINE_PARAMETERS);
    setValue(alignParametersInCallsCheckBox, settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    setValue(alignParenthesizedExpressionCheckBox, settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);

    setValue(indentSpinner, settings.INDENT);
    setValue(elseOnNewLineCheckBox, settings.ELSE_ON_NEW_LINE);
    setValue(whileOnNewLineCheckBox, settings.WHILE_ON_NEW_LINE);
    setValue(catchOnNewLineCheckBox, settings.CATCH_ON_NEW_LINE);
    setValue(finallyOnNewLineCheckBox, settings.FINALLY_ON_NEW_LINE);
    setValue(specialElseIfTreatmentCheckBox, settings.SPECIAL_ELSE_IF_TREATMENT);
    setValue(indentCaseFromMatchCheckBox, settings.INDENT_CASE_FROM_SWITCH);
  }

  private static void setValue(JSpinner spinner, int value) {
    spinner.setValue(value);
  }

  private static void setValue(@NotNull final JComboBox box, final int value) {
    box.setSelectedIndex(value);
  }

  private static void setValue(@NotNull final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }
}

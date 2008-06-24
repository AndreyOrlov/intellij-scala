package org.jetbrains.plugins.scala.refactor;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.refactor.introduceVariable.ScalaIntroduceVariableHandler;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
public class ScalaRefactoringSupportProvider extends DefaultRefactoringSupportProvider {

  public static final ScalaRefactoringSupportProvider INSTANCE = new ScalaRefactoringSupportProvider();

  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof ScTypeDefinition;
  }

  /**
   * @return handler for introducing local variables in Groovy
   */
  @Nullable
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new ScalaIntroduceVariableHandler();
  }

  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return null;
  }
}

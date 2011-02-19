package org.jetbrains.plugins.scala.injection;

import com.intellij.patterns.PsiJavaPatterns;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral;

/**
 * Pavel Fatin
 */

public class ScalaPatterns extends PsiJavaPatterns {
  public static ScalaElementPattern scalaLiteral() {
    return new ScalaElementPattern.Capture<ScLiteral>(ScLiteral.class);
  }
}

package org.jetbrains.plugins.scala.finder;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.caches.project.ScalaCachesManager;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class ScalaClassFinder implements ProjectComponent, PsiElementFinder {
  private Project myProject;

  public ScalaClassFinder(Project project) {
    myProject = project;
  }

  @Nullable
  public PsiClass findClass(@NotNull String qualifiedName, GlobalSearchScope scope) {
    return ScalaCachesManager.getInstance(myProject).getClassByName(qualifiedName, scope);
  }

  @NotNull
  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    return ScalaCachesManager.getInstance(myProject).getClassesByName(qualifiedName, scope);
  }

  @Nullable
  public PsiPackage findPackage(String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope) {
    return new PsiPackage[0];
  }

  @NotNull
  public PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final PsiDirectory dir : psiPackage.getDirectories(scope)) {
      for (final PsiFile file : dir.getFiles()) {
        if (file instanceof ScalaFile) {
          result.addAll(Arrays.asList(((ScalaFile) file).getTypeDefinitionsArray()));
        }
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Scala class finder";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}

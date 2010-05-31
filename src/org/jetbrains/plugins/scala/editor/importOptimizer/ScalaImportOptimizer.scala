package org.jetbrains.plugins.scala
package editor.importOptimizer


import collection.mutable.HashSet
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.toplevel.imports.ScImportStmt
import lang.psi.api.toplevel.imports.usages.{ImportUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportExprUsed}
import lang.resolve.ScalaResolveResult
import lang.psi.types.result.{TypingContext, TypeResult, Success}
import lang.psi.api.base.types.ScTypeElement
import collection.Set
import lang.psi.api.expr.{ScBlockExpr, ScReturnStmt, ScExpression}
import annotator.ScalaAnnotator
import lang.psi.types.{ScType, Unit}
import lang.psi.api.statements.{ScVariableDefinition, ScFunction, ScPatternDefinition}
import lang.psi.ScalaPsiElement
import lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.06.2009
 */

class ScalaImportOptimizer extends ImportOptimizer {
  def processFile(file: PsiFile): Runnable = {
    if (file.isInstanceOf[ScalaFile]) {
      val scalaFile: ScalaFile = file.asInstanceOf[ScalaFile]
      def getUnusedImports: HashSet[ImportUsed] = {
        val usedImports = new HashSet[ImportUsed]
        file.accept(new ScalaRecursiveElementVisitor {
          override def visitReference(ref: ScReferenceElement) = {
            if (PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null) {
              ref.multiResolve(false) foreach {
                case scalaResult: ScalaResolveResult =>
                  usedImports ++= scalaResult.importsUsed
                //println(ref.getElement.getText + " -- " + scalaResult.importsUsed + " -- " + scalaResult.element)
                case _ =>
              }
            }
            super.visitReference(ref)
          }

          override def visitElement(element: ScalaPsiElement) = {
            val imports = element match {
              case expression: ScExpression => {
                checkTypeForExpression(expression)
              }
              case _ => ScalaImportOptimizer.NO_IMPORT_USED
            }
            usedImports ++= imports
            super.visitElement(element)
          }
        })
        val unusedImports = new HashSet[ImportUsed]
        unusedImports ++= scalaFile.getAllImportUsed
        unusedImports --= usedImports
        unusedImports
      }
      new Runnable {
        def run: Unit = {
          val documentManager = PsiDocumentManager.getInstance(scalaFile.getProject)
          documentManager.commitDocument(documentManager.getDocument(scalaFile)) //before doing changes let's commit document
          //remove unnecessary imports
          val _unusedImports = getUnusedImports
          val unusedImports = new HashSet[ImportUsed]
          for (importUsed <- _unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                val toDelete = expr.reference match {
                  case Some(ref: ScReferenceElement) => {
                    ref.multiResolve(false).length > 0
                  }
                  case _ => {
                    !PsiTreeUtil.hasErrorElements(expr)
                  }
                }
                if (toDelete) {
                  unusedImports += importUsed
                }
              }
              case ImportWildcardSelectorUsed(expr) => {
                unusedImports += importUsed
              }
              case ImportSelectorUsed(sel) => {
                if (sel.reference.getText == sel.importedName && sel.reference.multiResolve(false).length > 0) {
                  unusedImports += importUsed
                }
              }
            }
          }
          for (importUsed <- unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                expr.deleteExpr
              }
              case ImportWildcardSelectorUsed(expr) => {
                expr.wildcardElement match {
                  case Some(element: PsiElement) => {
                    if (expr.selectors.length == 0) {
                      expr.deleteExpr
                    } else {
                      var node = element.getNode
                      var prev = node.getTreePrev
                      var t = node.getElementType
                      do {
                        t = node.getElementType
                        node.getTreeParent.removeChild(node)
                        node = prev
                        if (node != null) prev = node.getTreePrev
                      } while (node != null && t != ScalaTokenTypes.tCOMMA)
                    }
                  }
                  case _ =>
                }
              }
              case ImportSelectorUsed(sel) => {
                sel.deleteSelector
              }
            }
          }

          documentManager.commitDocument(documentManager.getDocument(scalaFile))
          //todo: add deleting unnecessary braces
          //todo: add removing blank lines (last)
          //todo: add other optimizing
        }
      }
    } else {
      EmptyRunnable.getInstance
    }
  }

  private def checkTypeForExpression(expr: ScExpression): Set[ImportUsed] = {
    expr.getTypeAfterImplicitConversion()._2
  }


  def supports(file: PsiFile): Boolean = {
    true
  }
}

object ScalaImportOptimizer {
  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
}
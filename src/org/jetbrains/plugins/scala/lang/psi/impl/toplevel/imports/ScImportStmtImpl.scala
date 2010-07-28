package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.openapi.util.Key
import com.intellij.lang.ASTNode

import com.intellij.util.ArrayFactory
import lang.resolve.{ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import _root_.scala.collection.mutable.HashSet
import parser.ScalaElementTypes
import psi.stubs.ScImportStmtStub
import usages._
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.processor._
import api.toplevel.typedef.ScTemplateDefinition
import types.ScDesignatorType
import collection.immutable.Set

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScImportStmtImpl extends ScalaStubBasedElementImpl[ScImportStmt] with ScImportStmt {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportStmtStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScImportStatement"

  import scope._

  def importExprs: Array[ScImportExpr] = getStubOrPsiChildren(ScalaElementTypes.IMPORT_EXPR, new ArrayFactory[ScImportExpr] {
    def create(count: Int): Array[ScImportExpr] = new Array[ScImportExpr](count)
  })

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    var i = 0
    while (i < importExprs.length) {
      val importExpr = importExprs(i)
      ProgressManager.checkCanceled
      if (importExpr == lastParent) return true
      val elemsAndUsages: Array[(PsiElement, collection.Set[ImportUsed])] = importExpr.reference match {
        case Some(ref) => {
          val arrayOfResults = ref.multiResolve(false)
          val usages = new Array[(PsiElement, collection.Set[ImportUsed])](arrayOfResults.length)
          var i = 0
          while (i < arrayOfResults.length) {
            usages(i) = arrayOfResults(i) match {
              case s: ScalaResolveResult => (s.getElement, s.importsUsed)
              case r: ResolveResult => (r.getElement, Set[ImportUsed]())
            }
            i = i + 1
          }
          usages
        }
        case _ => Array()
      }
      var j = 0
      while (j < elemsAndUsages.length) {
        val (elem, importsUsed) = elemsAndUsages(j)
        ProgressManager.checkCanceled
        importExpr.selectorSet match {
          case None =>
            // Update the set of used imports
            val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
            if (importExpr.singleWildcard) {
              (elem, processor) match {
                case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] => {
                  if (!processor.processType(new ScDesignatorType(cl, true), place.asInstanceOf[ScalaPsiElement],
                    state.put(ImportUsed.key, newImportsUsed))) return false
                }
                case _ => {
                  if (!elem.processDeclarations(processor, state.put(ImportUsed.key, newImportsUsed),
                    this, place)) return false
                }
              }
            } else {
              if (!processor.execute(elem, state.put(ImportUsed.key, newImportsUsed))) return false
            }
          case Some(set) => {
            val shadowed: HashSet[(ScImportSelector, PsiElement)] = HashSet.empty
            var selectors: Array[ScImportSelector] = set.selectors
            var k = 0
            while (k < selectors.length) {
              val selector = selectors(k)
              ProgressManager.checkCanceled
              var results: Array[ResolveResult] = selector.reference.multiResolve(false)
              var l = 0
              while (l < results.length) { //Resolve the name imported by selector
                // Collect shadowed elements
                val result = results(l)
                shadowed += ((selector, result.getElement))
                if (!processor.execute(result.getElement,
                  (state.put(ResolverEnv.nameKey, selector.importedName).
                          put(ImportUsed.key, Set(importsUsed.toSeq: _*) + ImportSelectorUsed(selector)).
                          put(BaseProcessor.boundClassKey,
                    result match {case result: ScalaResolveResult => result.boundClass case _ => null})
                          ))) {
                  return false
                }
                l = l + 1
              }
              k = k + 1
            }

            // There is total import from stable id
            // import a.b.c.{d=>e, f=>_, _}
            if (set.hasWildcard) {
              processor match {
                case bp: BaseProcessor => {
                  ProgressManager.checkCanceled
                  val p1 = new BaseProcessor(bp.kinds) {
                    override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                    override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) =
                      processor.handleEvent(event, associated)

                    override def execute(element: PsiElement, state: ResolveState): Boolean = {
                      // Register shadowing import selector
                      val elementIsShadowed = shadowed.find(p => elem.equals(p._2))

                      val newState = elementIsShadowed match {
                        case Some((selector, elem)) => {
                          val oldImports = state.get(ImportUsed.key)
                          val newImports = if (oldImports == null) Set[ImportUsed]() else oldImports

                          state.put(ImportUsed.key, Set(newImports.toSeq: _*) + ImportSelectorUsed(selector))
                        }
                        case None => state
                      }

                      if (elementIsShadowed != None) true else processor.execute(element, newState)
                    }
                  }

                  val newImportsUsed: Set[ImportUsed] = Set(importsUsed.toSeq: _*) + ImportWildcardSelectorUsed(importExpr)
                  (elem, processor) match {
                    case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] => {
                      if (!processor.processType(new ScDesignatorType(cl, true), place.asInstanceOf[ScalaPsiElement],
                        state.put(ImportUsed.key, newImportsUsed))) return false
                    }
                    case _ => {
                      if (!elem.processDeclarations(p1,
                        // In this case import optimizer should check for used selectors
                        state.put(ImportUsed.key, newImportsUsed),
                        this, place)) return false
                    }
                  }
                }
                case _ => true
              }

            }
          }
        }
        j = j + 1
      }
      i = i + 1
    }

    true
  }
}
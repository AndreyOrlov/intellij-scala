package org.jetbrains.plugins.scala.highlighter

import lang.psi.api.statements._
import com.intellij.psi._
import lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScBindingPattern}
import lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import lang.psi.api.expr.{ScAnnotationExpr, ScAnnotation, ScReferenceExpression, ScNameValuePair}
import lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.toplevel.ScEarlyDefinitions
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScObject}
import lang.lexer.ScalaTokenTypes

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2008
 */

object AnnotatorHighlighter {
  def highlightReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    refElement.resolve match {
      case _: ScSyntheticClass => { //this is first, it's important!
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.PREDEF)
      }
      case x: ScClass if x.getModifierList.has(ScalaTokenTypes.kABSTRACT) => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case _: ScTypeParam => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
      }
      case _: ScClass => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      }
      case _: ScObject => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      }
      case _: ScTrait => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      }
      case x: PsiClass if x.isInterface => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      }
      case x: PsiClass if x.getModifierList.hasModifierProperty("abstract") => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      }
      case _: PsiClass if refElement.isInstanceOf[ScStableCodeReferenceElement] => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      }
      case _: PsiClass if refElement.isInstanceOf[ScReferenceExpression] => {
        val annotation = holder.createInfoAnnotation(refElement, null)
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      }
      case x: ScBindingPattern => {
        var parent: PsiElement = x
        while (parent != null && !(parent.isInstanceOf[ScValue] || parent.isInstanceOf[ScVariable]
                || parent.isInstanceOf[ScCaseClause])) parent = parent.getParent
        parent match {
          case _: ScValue | _: ScVariable => {
            parent.getParent match {
              case _: ScTemplateBody | _: ScEarlyDefinitions => {
                parent.getParent.getParent.getParent match {
                  case _: ScClass | _: ScTrait | _: ScObject => {
                    val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                    parent match {
                      case _: ScPatternDefinition | _: ScVariableDefinition =>
                        annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DEFINITION)
                      case _ =>
                        annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DECLARATION)
                    }
                  }
                  case _ =>
                }
              }
              case _ => {
                val annotation = holder.createInfoAnnotation(refElement, null)
                annotation.setTextAttributes(DefaultHighlighter.LOCAL)
              }
            }
          }
          case _: ScCaseClause => {
            val annotation = holder.createInfoAnnotation(refElement, null)
            annotation.setTextAttributes(DefaultHighlighter.PATTERN)
          }
          case _ =>
        }
      }
      case x: PsiField => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DEFINITION)
      }
      case x: ScParameter => {
        val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
        annotation.setTextAttributes(DefaultHighlighter.PARAMETER)
      }
      case _: ScFunctionDefinition | _: ScFunctionDeclaration => {
        val x = refElement.resolve
        if (x != null) {
          x.getParent match {
            case _: ScTemplateBody | _: ScEarlyDefinitions => {
              x.getParent.getParent.getParent match {
                case _: ScClass | _: ScTrait => {
                  val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                  annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
                }
                case _: ScObject => {
                  val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
                  annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
                }
                case _ =>
              }
            }
            case _ => {
              val annotation = holder.createInfoAnnotation(refElement, null)
              annotation.setTextAttributes(DefaultHighlighter.LOCAL_METHOD_CALL)
            }
          }
        }
      }
      case x: PsiMethod => {
        if (x.getModifierList.hasModifierProperty("static")) {
          val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
          annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
        } else {
          val annotation = holder.createInfoAnnotation(refElement.getLastChild, null)
          annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
        }
      }
      case x => //println("" + x + " " + x.getText)
    }
  }

  def highlightElement(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScAnnotation => visitAnnotation(x, holder)
      case x: ScClass => visitClass(x, holder)
      case x: ScParameter => visitParameter(x, holder)
      case x: ScCaseClause => visitCaseClause(x, holder)
      case _ if element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER => {
        element.getParent match {
          case _: ScNameValuePair => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.ANNOTATION_ATTRIBUTE)
          }
          case _: ScTypeParam => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
          }
          case _: ScObject => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.OBJECT)
          }
          case _: ScTrait => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TRAIT)
          }
          case x: ScBindingPattern => {
            var parent: PsiElement = x
            while (parent != null && !(parent.isInstanceOf[ScValue] || parent.isInstanceOf[ScVariable])) parent = parent.getParent
            parent match {
              case _: ScValue | _: ScVariable => {
                parent.getParent match {
                  case _: ScTemplateBody | _: ScEarlyDefinitions => {
                    parent.getParent.getParent.getParent match {
                      case _: ScClass | _: ScTrait | _: ScObject => {
                        parent match {
                          case _: ScPatternDefinition | _: ScVariableDefinition => {
                            val annotation = holder.createInfoAnnotation(element, null)
                            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DEFINITION)
                          }
                          case _ => {
                            val annotation = holder.createInfoAnnotation(element, null)
                            annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DECLARATION)
                          }
                        }
                      }
                      case _ =>
                    }
                  }
                  case _ => {
                    val annotation = holder.createInfoAnnotation(element, null)
                    annotation.setTextAttributes(DefaultHighlighter.LOCAL)
                  }
                }
              }
              case _ =>
            }
          }
          case _: ScFunctionDefinition | _: ScFunctionDeclaration => {
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.METHOD_DECLARATION)
          }
          case _ =>
        }
      }
      case _ =>
    }
  }

  private def visitAnnotation(annotation: ScAnnotation, holder: AnnotationHolder): Unit = {
    val annotation1 = holder.createInfoAnnotation(annotation.getFirstChild, null)
    annotation1.setTextAttributes(DefaultHighlighter.ANNOTATION)
    val element = annotation.annotationExpr.constr.typeElement
    val annotation2 = holder.createInfoAnnotation(element, null)
    annotation2.setTextAttributes(DefaultHighlighter.ANNOTATION)
  }

  private def visitClass(clazz: ScClass, holder: AnnotationHolder): Unit = {
    if (clazz.getModifierList.has(ScalaTokenTypes.kABSTRACT)) {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
    } else {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.CLASS)
    }
    for (vall <- clazz.allVals; name <- vall.declaredElements) {
      val annotation = holder.createInfoAnnotation(name, null)
      vall match {
        case _: ScPatternDefinition =>
          annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DEFINITION)
        case _ =>
          annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DECLARATION)
      }
    }
    for (varl <- clazz.allVars; name <- varl.declaredElements) {
      val annotation = holder.createInfoAnnotation(name, null)
      varl match {
        case _: ScVariableDefinition =>
          annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DEFINITION)
        case _ =>
          annotation.setTextAttributes(DefaultHighlighter.CLASS_FIELD_DECLARATION)
      }
    }
  }

  private def visitParameter(param: ScParameter, holder: AnnotationHolder): Unit = {
    val annotation = holder.createInfoAnnotation(param.nameId, null)
    annotation.setTextAttributes(DefaultHighlighter.PARAMETER)
  }

  private def visitPattern(pattern: ScPattern, holder: AnnotationHolder): Unit = {
    for (binding <- pattern.bindings if !binding.isWildcard) {
      val annotation = holder.createInfoAnnotation(binding, null)
      annotation.setTextAttributes(DefaultHighlighter.PATTERN)
    }
  }

  private def visitCaseClause(clause: ScCaseClause, holder: AnnotationHolder): Unit = {
    clause.pattern match {
      case Some(x) => visitPattern(x, holder)
      case None =>
    }
  }
}
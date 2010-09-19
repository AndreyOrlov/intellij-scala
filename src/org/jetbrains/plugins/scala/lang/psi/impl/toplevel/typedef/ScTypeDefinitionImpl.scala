package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

/**
 * @author ilyas
 */

import _root_.java.lang.String
import _root_.java.util.{List, ArrayList}
import com.intellij.openapi.util.{Pair, Iconable}
import api.ScalaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import _root_.scala.collection.immutable.Set
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.psi._
import com.intellij.openapi.editor.colors._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.impl._
import com.intellij.util.VisibilityIcons
import javax.swing.Icon
import psi.stubs.ScTemplateDefinitionStub
import stubs.StubElement
import synthetic.JavaIdentifier
import Misc._
import source.PsiFileImpl
import types._
import fake.FakePsiMethod
import api.base.patterns.ScBindingPattern
import api.base.{ScPrimaryConstructor, ScModifierList}
import api.toplevel.{ScToplevelElement, ScTypedDefinition}
import com.intellij.openapi.project.DumbService
import nonvalue.Parameter
import result.{TypeResult, Failure, Success, TypingContext}
import util.{PsiModificationTracker, PsiUtil, PsiTreeUtil}
import com.intellij.openapi.progress.ProgressManager
import collection.{Seq, Iterable}
import api.statements.{ScVariable, ScValue, ScAnnotationsHolder}
import api.statements.params.ScClassParameter
import com.intellij.openapi.util.text.StringUtil

abstract class ScTypeDefinitionImpl extends ScalaStubBasedElementImpl[ScTemplateDefinition] with ScTypeDefinition with PsiClassFake {
  override def add(element: PsiElement): PsiElement = {
    element match {
      case mem: ScMember => addMember(mem, None)
      case _ => super.add(element)
    }
  }

  def getType(ctx: TypingContext)  = {
    if (typeParameters.length == 0)
      Success(ScDesignatorType(this), Some(this))
    else {
      Success(ScParameterizedType(ScDesignatorType(this), typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty))), Some(this))
    }
  }

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType] = {
    def args: Seq[ScTypeParameterType] = typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty))
    def innerType = if (typeParameters.length == 0) ScDesignatorType(this)
                    else ScParameterizedType(ScDesignatorType(this), args)
    val parentClazz = ScalaPsiUtil.getPlaceTd(this)
    if (parentClazz != null) {
      val innerProjection: ScProjectionType = ScProjectionType(if (!thisProjections)
        parentClazz.getTypeWithProjections(TypingContext.empty, false).getOrElse(
        return Failure("Cannot resolve parent class", Some(this)))
        else ScThisType(parentClazz),
        this, ScSubstitutor.empty)
      Success(if (typeParameters.length == 0) innerProjection
              else ScParameterizedType(innerProjection, args), Some(this))
    } else Success(innerType, Some(this))
  }

  override def getModifierList: ScModifierList = super[ScTypeDefinition].getModifierList

  override def hasModifierProperty(name: String): Boolean = super[ScTypeDefinition].hasModifierProperty(name)

  override def getNavigationElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorClass
    case _ => this
  }

  private def hasSameScalaKind(other: PsiClass) = (this, other) match {
    case (_: ScTrait, _: ScTrait)
            | (_: ScObject, _: ScObject)
            | (_: ScClass, _: ScClass) => true
    case _ => false
  }

  private def getSourceMirrorClass: PsiClass = {
    val classParent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    val name = getName
    if (classParent == null) {
      val classes: Array[PsiClass] = getContainingFile.getNavigationElement.asInstanceOf[PsiClassOwner].getClasses
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val c = classesIterator.next
        if (name == c.getName && hasSameScalaKind(c)) return c
      }
    } else {
      val parentSourceMirror = classParent.asInstanceOf[ScTypeDefinitionImpl].getSourceMirrorClass
      parentSourceMirror match {
        case td: ScTypeDefinitionImpl => for (i <- td.typeDefinitions if name == i.getName && hasSameScalaKind(i))
          return i
        case _ => this
      }
    }
    this
  }

  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getContainingClass = super[ScTypeDefinition].getContainingClass

  override def getQualifiedName: String = qualifiedName(".")

  def getQualifiedNameForDebugger: String = qualifiedName("$")

  private def qualifiedName(classSeparator: String): String = {

    // Returns prefix with convenient separator sep
    def _packageName(e: PsiElement, sep: String, k: (String) => String): String = e.getParent match {
      case t: ScTypeDefinition => _packageName(t, sep, (s) => k(s + t.name + sep))
      case p: ScPackaging => _packageName(p, ".", (s) => k(s + p.getPackageName + "."))
      case f: ScalaFile => val pn = f.getPackageName; k(if (pn.length > 0) pn + "." else "")
      case _: PsiFile | null => k("")
      case parent => _packageName(parent, sep, identity _)
    }

    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].qualName
    } else {
      val packageName = _packageName(this, classSeparator, identity _)
      packageName + name
    }
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText(): String = name

      def getTextAttributesKey(): TextAttributesKey = null

      def getLocationString(): String = getPath match {
        case "" => "<default>"
        case p => '(' + p + ')'
      }

      override def getIcon(open: Boolean) = ScTypeDefinitionImpl.this.getIcon(0)
    }
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    val filterFun = (m: PsiMethod) => m.getName == name
    val arrayOfMethods: Array[PsiMethod] = if (checkBases) getAllMethods else functions.toArray[PsiMethod]
    arrayOfMethods.filter(filterFun)
  }

  override def checkDelete() {
  }

  override def delete() = {
    var toDelete: PsiElement = this
    var parent: PsiElement = getParent
    while (parent.isInstanceOf[ScToplevelElement] && parent.asInstanceOf[ScToplevelElement].typeDefinitions.length == 1) {
      toDelete = parent
      parent = toDelete.getParent
    }
    toDelete match {
      case file: ScalaFile => file.delete
      case _ => parent.getNode.removeChild(toDelete.getNode)
    }
  }

  override def getTypeParameters = typeParameters.toArray

  override def getSupers: Array[PsiClass] = {
    val direct = extendsBlock.supers.toArray
    val res = new ArrayBuffer[PsiClass]
    res ++= direct
    for (sup <- direct if !res.contains(sup)) res ++= sup.getSupers
    // return strict superclasses
    res.filter(_ != this).toArray
  }

  /**
   * Do not use it for scala. Use functions method instead.
   */
  override def getMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    def methods(td: ScTemplateDefinition): Seq[PsiMethod] = {
      td.members.flatMap {
        p => {
          import api.statements.{ScVariable, ScFunction, ScValue}
          import synthetic.PsiMethodFake
          p match {
            case primary: ScPrimaryConstructor => Array[PsiMethod](primary)
            case function: ScFunction => Array[PsiMethod](function)
            case value: ScValue => {
              for (binding <- value.declaredElements) yield new FakePsiMethod(binding, value.hasModifierProperty _)
            }
            case variable: ScVariable => {
              for (binding <- variable.declaredElements) yield new FakePsiMethod(binding, variable.hasModifierProperty _)
            }
            case _ => Array[PsiMethod]()
          }
        }
      }
    }
    buffer ++= methods(this)
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(td: ScTemplateDefinition) => buffer ++= methods(td) //to see from Java methods from companion modules.
      case _ =>
    }
    return buffer.toArray
  }

  override def getAllMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    val methodsIterator = TypeDefinitionMembers.getMethods(this).iterator
    while (methodsIterator.hasNext) {
      val method = methodsIterator.next._1.method
      buffer += method
    }

    buffer ++= syntheticMembers
    val valsIterator = TypeDefinitionMembers.getVals(this).iterator
    while (valsIterator.hasNext) {
      val t = valsIterator.next._1
      t match {
        case t: ScTypedDefinition => {
          implicit def arr2arr(a: Array[ScType]): Array[Parameter] = a.map(Parameter("", _, false, false))
          val context = ScalaPsiUtil.nameContext(t)
          buffer += new FakePsiMethod(t, context match {
            case o: PsiModifierListOwner => o.hasModifierProperty _
            case _ => (s: String) => false
          })

          //todo: this is duplicate
          context match {
            case annotated: ScAnnotationsHolder => {
              val annotations = annotated.annotations
              annotated.hasAnnotation("scala.reflect.BeanProperty") match {
                case Some(_) => {
                  context match {
                    case value: ScValue => {
                      buffer += new FakePsiMethod(t, "get" + t.getName.capitalize,
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), value.hasModifierProperty _)
                    }
                    case variable: ScVariable => {
                      buffer += new FakePsiMethod(t, "get" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), variable.hasModifierProperty _)
                      buffer += new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                        Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, variable.hasModifierProperty _)
                    }
                    case param: ScClassParameter if param.isVal => {
                      buffer += new FakePsiMethod(t, "get" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), param.hasModifierProperty _)
                    }
                    case param: ScClassParameter if param.isVar => {
                      buffer += new FakePsiMethod(t, "get" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), param.hasModifierProperty _)
                      buffer += new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                        Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, param.hasModifierProperty _)
                    }
                    case _ =>
                  }
                }
                case _ =>
              }

              annotated.hasAnnotation("scala.reflect.BooleanBeanProperty") match {
                case Some(_) => {
                  context match {
                    case value: ScValue => {
                      buffer += new FakePsiMethod(t, "is" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), value.hasModifierProperty _)
                    }
                    case variable: ScVariable => {
                      buffer += new FakePsiMethod(t, "is" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), variable.hasModifierProperty _)
                      buffer += new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                        Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, variable.hasModifierProperty _)
                    }
                    case param: ScClassParameter => {
                      if (param.isVal || param.isVar) buffer += new FakePsiMethod(t, "is" + StringUtil.capitalize(t.getName),
                        Array.empty, t.getType(TypingContext.empty).getOrElse(Any), param.hasModifierProperty _)
                      if (param.isVal) buffer += new FakePsiMethod(t, "set" + StringUtil.capitalize(t.getName),
                        Array[ScType](t.getType(TypingContext.empty).getOrElse(Any)), Unit, param.hasModifierProperty _)
                    }
                    case _ =>
                  }
                }
                case None =>
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
    }
    //todo: methods from companion module?
    return buffer.toArray
  }

  import com.intellij.psi.scope.PsiScopeProcessor

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean, visited: Set[PsiClass]): Boolean = {
      if (visited.contains(drv)) false
      else drv match {
        case drg: ScTypeDefinition => drg.superTypes.find{
          t => ScType.extractClass(t) match {
            case Some(c) => {
              val value = baseClass match { //todo: it was wrong to write baseClass.isInstanceOf[c.type]
                case _: ScTrait if c.isInstanceOf[ScTrait] => true
                case _: ScClass if c.isInstanceOf[ScClass] => true
                case _ if !c.isInstanceOf[ScTypeDefinition] => true
                case _ => false
              }
              (c.getQualifiedName == baseClass.getQualifiedName && value) || (deep && isInheritorInner(base, c, deep, visited + drg))
            }
            case _ => false
          }
        }
        case _ => drv.getSuperTypes.find{
          psiT =>
                  val c = psiT.resolveGenerics.getElement
                  if (c == null) false else c == baseClass || (deep && isInheritorInner(base, c, deep, visited + drv))
        }
      }
    }
    if (DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indecies
    isInheritorInner(baseClass, this, deep, Set.empty)
  }

  

  def signaturesByName(name: String): Iterable[PhysicalSignature] =
    for ((_, n) <- TypeDefinitionMembers.getMethods(this) if n.info.method.getName == name) yield
      new PhysicalSignature(n.info.method, n.info.substitutor)

  override def getNameIdentifier: PsiIdentifier = {
    Predef.assert(nameId != null, "Class hase null nameId. Class text: " + getText) //diagnostic for EA-20122
    new JavaIdentifier(nameId)
  }

  override def getIcon(flags: Int): Icon = {
    val icon = getIconInner
    return icon //todo: remove, when performance issues will be fixed
    if (!this.isValid) return icon //to prevent Invalid access: EA: 13535
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable
    val rowIcon = ElementBase.createLayeredIcon(icon, ElementPresentationUtil.getFlags(this, isLocked))
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      val accessLevel = {
        if (hasModifierProperty("private")) PsiUtil.ACCESS_LEVEL_PRIVATE
        else if (hasModifierProperty("protected")) PsiUtil.ACCESS_LEVEL_PROTECTED
        else PsiUtil.ACCESS_LEVEL_PUBLIC
      }
      VisibilityIcons.setVisibilityIcon(accessLevel, rowIcon);
    }
    rowIcon
  }

  protected def getIconInner: Icon

  override def getImplementsListTypes = getExtendsListTypes

  override def getExtendsListTypes = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents
      tp match {
        case Some(tp1) => (for (te <- tp1.typeElements;
                                t = te.getType(TypingContext.empty).getOrElse(Any);
                                asPsi = ScType.toPsi(t, getProject, GlobalSearchScope.allScope(getProject));
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }


  override def getDocComment: PsiDocComment = super[ScTypeDefinition].getDocComment


  override def isDeprecated: Boolean = super[ScTypeDefinition].isDeprecated

  //Java sources uses this method. Really it's not very useful. Parameter checkBases ignored
  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = {
    super[ScTypeDefinition].findMethodsAndTheirSubstitutorsByName(name, checkBases)
  }

  override def getInnerClasses: Array[PsiClass] = {
    members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray
  }

  override def getAllInnerClasses: Array[PsiClass] = {
    members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray  //todo: possible add base classes inners
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    (if (checkBases) {
      //todo: possibly add base classes inners
      members.find(p => p.isInstanceOf[PsiClass] && p.asInstanceOf[PsiClass].getName == name).getOrElse(null)
    } else {
      members.find(p => p.isInstanceOf[PsiClass] && p.asInstanceOf[PsiClass].getName == name).getOrElse(null)
    }).asInstanceOf[PsiClass]
  }
}
package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsStubBuilderFactory

import com.intellij.psi.stubs.{PsiFileStubImpl, PsiFileStub}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiManager, PsiFile}
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory
import decompiler.DecompilerUtil.DecompilationResult
import com.intellij.openapi.project.{Project, ProjectManager}
import reflect.NameTransformer

/**
 * @author ilyas
 */

class ScClsStubBuilderFactory extends ClsStubBuilderFactory[ScalaFile] {
  def buildFileStub(vFile: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    buildFileStub(vFile, bytes, ProjectManager.getInstance().getDefaultProject)
  }

  override def buildFileStub(vFile: VirtualFile, bytes: Array[Byte], project: Project): PsiFileStub[ScalaFile] = {
    val DecompilationResult(_, source, text, _) = DecompilerUtil.decompile(vFile, bytes)
    val file = ScalaPsiElementFactory.createScalaFile(text.replace("\r", ""), PsiManager.getInstance(project))

    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(vFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder.buildStubTree(file)
    stub.asInstanceOf[PsiFileStubImpl[PsiFile]].setPsi(null)
    stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }

  def canBeProcessed(file: VirtualFile, bytes: Array[Byte]): Boolean = {
    val name: String = file.getNameWithoutExtension
    if (name.endsWith("$")) {
      val parent: VirtualFile = file.getParent
      val child: VirtualFile = parent.findChild(name.dropRight(1) + ".class")
      if (child != null) {
        val res = DecompilerUtil.isScalaFile(child)
        if (res) return true //let's handle it separately to avoid giving it for Java.
      }
    }
    DecompilerUtil.isScalaFile(file, bytes)
  }

  def isInnerClass(file: VirtualFile): Boolean = {
    if (file.getExtension != "class") return false
    val name: String = file.getNameWithoutExtension
    val parent: VirtualFile = file.getParent
    if (name.endsWith("$")) {
      val child: VirtualFile = parent.findChild(name.dropRight(1) + ".class")
      if (child != null) {
        val res = DecompilerUtil.isScalaFile(child)
        if (res) return false //let's handle it separately to avoid giving it for Java.
      }
    }
    isInner(name, new ParentDirectory(parent))
  }

  private def isInner(name: String, directory: Directory): Boolean = {
    if (name.endsWith("$") && directory.contains(name.dropRight(1))) {
      return false //let's handle it separately to avoid giving it for Java.
    }
    isInner(NameTransformer.decode(name), 0, directory)
  }

  private def isInner(name: String, from: Int, directory: Directory): Boolean = {
    val index: Int = name.indexOf('$', from)
    index != -1 && (containsPart(directory, name, index) || isInner(name, index + 1, directory))
  }

  private def containsPart(directory: Directory, name: String, endIndex: Int): Boolean = {
    endIndex > 0 && directory.contains(name.substring(0, endIndex))
  }

  private trait Directory {
    def contains(name: String): Boolean
  }

  private class ParentDirectory(dir: VirtualFile) extends Directory {
    def contains(name: String): Boolean = {
      if (dir == null) return false
      !dir.getChildren.forall(child =>
        child.getExtension != "class" || NameTransformer.decode(child.getNameWithoutExtension) == name
      )
    }
  }
}
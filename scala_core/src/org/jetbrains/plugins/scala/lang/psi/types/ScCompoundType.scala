package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import _root_.scala.collection.mutable.{HashSet, HashMap}
import api.statements._
import com.intellij.psi.PsiTypeParameter

case class ScCompoundType(val components: Seq[ScType], val decls: Seq[ScDeclaration], val typeDecls: Seq[ScTypeAlias]) extends ScType{
  //compound types are checked by checking the set of signatures in their refinements
  val signatureMap = new HashMap[Signature, ScType] {
    override def elemHashCode(s : Signature) = s.name.hashCode* 31 + s.types.length
  }

  type Bounds = Pair[ScType, ScType]
  val types = new HashMap[String, Bounds]

  for (typeDecl <- typeDecls) {
    types += ((typeDecl.name, (typeDecl.lowerBound, typeDecl.upperBound)))
  }

  for (decl <- decls) {
    decl match {
      case fun: ScFunctionDeclaration =>
        ((signatureMap += new PhysicalSignature(fun, ScSubstitutor.empty), fun.calcType))
      case varDecl: ScVariableDeclaration => {
        varDecl.typeElement match {
          case Some(te) => for (e <- varDecl.declaredElements) {
            signatureMap += ((new Signature(e.name, Seq.empty, Array(), ScSubstitutor.empty), te.getType))
            signatureMap += ((new Signature(e.name + "_", Seq.single(te.getType), Array(), ScSubstitutor.empty), Unit)) //setter
          }
          case None =>
        }
      }
      case valDecl: ScValueDeclaration => valDecl.typeElement match {
        case Some(te) => for (e <- valDecl.declaredElements) {
          signatureMap += ((new Signature(e.name, Seq.empty, Array(), ScSubstitutor.empty), te.getType))
        }
        case None =>
      }
    }
  }

  override def equiv(t: ScType) = t match {
    case other : ScCompoundType => {
      components.equalsWith (other.components) (_ equiv _) &&
      signatureMap.size == other.signatureMap.size &&
      signatureMap.elements.forall {entry => signatureMap.get(entry._1) match {
        case None => false
        case Some(t) => entry._2 equiv t
      }
      } &&
      typesMatch(types, other.types)
    }
    case _ => false
  }

  private def typesMatch(types1 : HashMap[String, Bounds],
                         types2 : HashMap[String, Bounds]) : Boolean = {
    if (types1 != types.size) return false
    else {
      for ((name, bounds1) <- types1) {
        types2.get(name) match {
          case None => return false
          case Some (bounds2) => if (!(bounds1._1 equiv bounds2._1) ||
                                     !(bounds1._2 equiv bounds2._2)) return false
        }
      }
      true
    }
  }
}
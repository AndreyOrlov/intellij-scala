package org.jetbrains.plugins.scala.lang.psi.types

import api.expr.{ScSuperReference, ScThisReference}
import api.base.{ScReferenceElement, ScPathElement}
import api.toplevel.ScTyped
/**
* @author ilyas
*/

case class ScSingletonType(path: ScPathElement) extends ScType {
  lazy /* to prevent SOE */
  val pathType = path match {
    case ref: ScReferenceElement => ref.bind match {
      case None => Nothing
      case Some(r) => r.element match {
        case typed: ScTyped => typed.calcType
        case e => new ScDesignatorType(e)
      }
    }
    case thisPath: ScThisReference => thisPath.refClass match {
      case Some(clazz) => new ScDesignatorType(clazz)
      case _ => Nothing
    }
    case superPath: ScSuperReference => superPath.refClass match {
      case Some(clazz) => new ScDesignatorType(clazz)
      case _ => Nothing
    }
  }

  override def equiv(t: ScType) = t match {
    case ScSingletonType(path1) => {
      def equiv(e1: ScPathElement, e2: ScPathElement): Boolean = {
        (e1, e2) match {
          case (r1: ScReferenceElement, r2: ScReferenceElement) =>
            r1.bind == r2.bind && ((r1.qualifier, r2.qualifier) match {
              case (Some(q1 : ScPathElement), Some(q2 : ScPathElement)) => equiv(q1, q2)
              case (None, None) => true
              case _ => false
            })
          case (t1: ScThisReference, t2: ScThisReference) => t1.refClass == t2.refClass
          case (s1: ScSuperReference, s2: ScSuperReference) => s1.refClass == s2.refClass &&
                  //we can come to the same classes from different outer classes' "super"
                  ((s1.qualifier, s2.qualifier) match {
                    case (Some(q1), Some(q2)) => equiv(q1, q2)
                    case (None, None) => true
                    case _ => false
                  })
          case _ => false
        }
      }
      equiv(path, path1)
    }
    case _ => false
  }
}
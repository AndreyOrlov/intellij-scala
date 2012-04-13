package org.jetbrains.plugins.scala
package lang.dependency

import base.SimpleTestCase
import org.junit.Assert
import org.intellij.lang.annotations.Language

/**
 * Pavel Fatin
 */

class DependencyTest extends SimpleTestCase {
  def testClass() {
    assertDependenciesAre("""
    object O {
      class C
      classOf[C]
    }
    """, ("C", "ScClass", "O.C"))
  }

  def testSyntheticClass() {
    assertDependenciesAre("""
    object O {
      classOf[Int]
    }
    """)
  }

  def testObject() {
    assertDependenciesAre("""
    object O {
      object Foo
      Foo
    }
    """, ("Foo", "ScObject", "O.Foo"))
  }
  
  def testQualifier() {
    assertDependenciesAre("""
    object O {
      object Foo
    }
    O.Foo
    """, ("O", "ScObject", "O"))
  }

  def testPrimaryConstructor() {
    assertDependenciesAre("""
    object O {
      class C
      new C
    }
    """, ("C", "PrimaryConstructor", "O.C"))
  }

  def testSecondaryConstructor() {
    assertDependenciesAre("""
    object O {
      class C(i: Int, s: String) {
        def this(i: Int) {
          this(i, "")
        }    
      }
      new C(1)
    }
    """, ("C", "ScFunctionDefinition", "O.C"))
  }

  def testCaseClassCopy() {
    assertDependenciesAre("""
    object O {
      case class C(v: Any)
      C(null).copy(v = null)
    }
    """, ("C", "ScFunctionDefinition", "O.C"))
  }


  def testSyntheticApply() {
    assertDependenciesAre("""
    object O {
      case class C()
      C()
    }
    """, ("C", "ScFunctionDefinition", "O.C"))
  }

  def testSyntheticUnapply() {
    assertDependenciesAre("""
    object O {
      case class C()
      null match {
        case C() =>
      }
    }
    """, ("C", "ScFunctionDefinition", "O.C"))
  }

  def testSyntheticUnapplySeq() {
    assertDependenciesAre("""
    object O {
      case class C(seq: Any*)
      null match {
        case C(1, 2, 3) =>
      }
    }
    """, ("C", "ScFunctionDefinition", "O.C"))
  }

  def testExplicitApply() {
    assertDependenciesAre("""
    object O {
      object Foo {
        def apply() {}
      }
      Foo()
    }
    """, ("Foo", "ScFunctionDefinition", "O.Foo"))
  }

  def testExplicitUnapply() {
    assertDependenciesAre("""
    object O {
      object Foo {
        def unapply() {}
      }
      null match {
        case Foo() =>
      }
    }
    """, ("Foo", "ScFunctionDefinition", "O.Foo"))
  }

  def testExplicitUnapplySeq() {
    assertDependenciesAre("""
    object O {
      object Foo {
        def unapplySeq(): Seq[Any] = null
      }
      null match {
        case Foo(1, 2, 3) =>
      }
    }
    """, ("Foo", "ScFunctionDefinition", "O.Foo"))
  }

  def testFunction() {
    assertDependenciesAre("""
    object O {
      def foo() {}
      foo()
    }
    """, ("foo", "ScFunctionDefinition", "O.foo"))
  }

  def testValue() {
    assertDependenciesAre("""
    object O {
      val foo = 1
      foo
    }""", ("foo", "ReferencePattern", "O.foo"))
  }

  def testVariable() {
    assertDependenciesAre("""
    object O {
      var foo = 1
      foo
    }""", ("foo", "ReferencePattern", "O.foo"))
  }

  // package
  // implicit conversions

  private def assertDependenciesAre(@Language("Scala") code: String, expectations: (String, String, String)*) {
    val file = parseText(code)

    val descriptors = Dependency.dependenciesIn(file)
            .map(it => (it.source.getText, it.target.toString, it.path.asString))

    Assert.assertEquals(expectations.toList, descriptors.toList)
  }
}
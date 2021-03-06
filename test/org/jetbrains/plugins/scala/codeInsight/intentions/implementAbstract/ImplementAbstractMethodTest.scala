package org.jetbrains.plugins.scala
package codeInsight.intentions.implementAbstract

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import com.intellij.codeInsight.intention.impl.ImplementAbstractMethodAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Nikolay.Tropin
 * 12/27/13
 */
class ImplementAbstractMethodTest extends ScalaIntentionTestBase {
  def familyName: String = new ImplementAbstractMethodAction().getFamilyName

  val START = CodeInsightTestFixture.SELECTION_START_MARKER
  val END = CodeInsightTestFixture.SELECTION_END_MARKER

  def testFromTrait() {
    val text =
      """
        |trait A {
        |  def <caret>f: Int
        |}
        |
        |class AA extends A
      """
    val result =
      s"""
        |trait A {
        |  def f: Int
        |}
        |
        |class AA extends A {
        |  override def f: Int = $START???$END
        |}"""
    doTest(text, result)
  }

  def testFromAbstractClass() {
    val text =
      """
        |abstract class A {
        |  def <caret>f: Int
        |}
        |
        |class AA extends A {}
      """
    val result =
      s"""
        |abstract class A {
        |  def f: Int
        |}
        |
        |class AA extends A {
        |  override def f: Int = $START???$END
        |}"""
    doTest(text, result)
  }

  def testParameterizedTrait() {
    val text =
      """
        |trait A[T] {
        |  def <caret>f: T
        |}
        |
        |class AA extends A[Int] {}
      """
    val result =
      s"""
        |trait A[T] {
        |  def f: T
        |}
        |
        |class AA extends A[Int] {
        |  override def f: Int = $START???$END
        |}"""
    doTest(text, result)
  }

  def testFunDefInTrait() {
    val text =
      """
        |trait A {
        |  def <caret>f: Int = 0
        |}
        |
        |class AA extends A
      """
    checkIntentionIsNotAvailable(text)
  }

  def testUnitReturn() {
    val text =
      """
        |trait A {
        |  def <caret>f
        |}
        |
        |class AA extends A
      """
    val result =
      s"""
        |trait A {
        |  def f
        |}
        |
        |class AA extends A {
        |  override def f: Unit = $START???$END
        |}"""
    doTest(text, result)
  }

}

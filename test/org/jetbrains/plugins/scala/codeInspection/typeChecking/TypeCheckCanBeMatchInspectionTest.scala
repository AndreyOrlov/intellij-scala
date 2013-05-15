package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import com.intellij.codeInsight.CodeInsightTestCase
import org.jetbrains.plugins.scala.codeInspection.parentheses.UnnecessaryParenthesesInspection

/**
 * Nikolay.Tropin
 * 5/15/13
 */
class TypeCheckCanBeMatchInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter{
  val START = CodeInsightTestCase.SELECTION_START_MARKER
  val END = CodeInsightTestCase.SELECTION_END_MARKER
  val annotation = TypeCheckCanBeMatchInspection.inspectionId
  val hint = TypeCheckCanBeMatchInspection.inspectionName

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[TypeCheckCanBeMatchInspection])
  }

  private def testFix(text: String, result: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[TypeCheckCanBeMatchInspection])
  }

  def test_1() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  x.toString
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int]) {
                 |  x.toString
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case _: Int =>
                   |    x.toString
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_2() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                     |if (x.isInstanc<caret>eOf[Int]) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case i: Int =>
                   |    i.toString
                   |    println(i)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_3() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  val y = x.asInstanceOf[Int]
                     |  x.asInstanceOf[Int].toString
                     |  println(y)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (x.isInstanc<caret>eOf[Int]) {
                 |  val y = x.asInstanceOf[Int]
                 |  x.asInstanceOf[Int].toString
                 |  println(y)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case y: Int =>
                   |    y.toString
                   |    println(y)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_4() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case y: Int if y == 1 =>
                   |    println(y)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_5() {
    val selected = s"""val x = 0
                     |if (x > 0 && (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1)) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (x > 0 && (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1)) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case y: Int if x > 0 && y == 1 =>
                   |    println(y)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_6() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else if (x.isInstanceOf[Long]) {
                     |  println(x)
                     |} else println()""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int]) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else if (x.isInstanceOf[Long]) {
                 |  println(x)
                 |} else println()""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case y: Int =>
                   |    println(y)
                   |  case _: Long =>
                   |    println(x)
                   |  case _ => println()
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_7() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Long] == 1) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else if (x.isInstanceOf[Long]) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else {
                     |  println(x)
                     |  println()
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Long] == 1) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else if (x.isInstanceOf[Long]) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else {
                 |  println(x)
                 |  println()
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |x match {
                   |  case y: Int if x.asInstanceOf[Long] == 1 =>
                   |    println(y)
                   |  case _: Long =>
                   |    val y = x.asInstanceOf[Int]
                   |    println(y)
                   |  case _ =>
                   |    println(x)
                   |    println()
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_8a() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (${START}x1.isInstanceOf[Int]$END && x2.isInstanceOf[Int]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x1 = 0
                 |val x2 = 0
                 |if (x<caret>1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x1 = 0
                   |val x2 = 0
                   |x1 match {
                   |  case y1: Int if x2.isInstanceOf[Int] =>
                   |    val y2 = x2.asInstanceOf[Int]
                   |    println(y1 + y2)
                   |  case _: Long if x2.isInstanceOf[Long] =>
                   |    val y1 = x1.asInstanceOf[Int]
                   |    val y2 = x2.asInstanceOf[Int]
                   |    println(y1 + y2)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_8b() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (x1.isInstanceOf[Int] && ${START}x2.isInstanceOf[Int]$END) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = s"""val x1 = 0
                 |val x2 = 0
                 |if (x1.isInstanceOf[Int] && <caret>x2.isInstanceOf[Int]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x1 = 0
                   |val x2 = 0
                   |x2 match {
                   |  case y2: Int if x1.isInstanceOf[Int] =>
                   |    val y1 = x1.asInstanceOf[Int]
                   |    println(y1 + y2)
                   |  case _: Long if x1.isInstanceOf[Long] =>
                   |    val y1 = x1.asInstanceOf[Int]
                   |    val y2 = x2.asInstanceOf[Int]
                   |    println(y1 + y2)
                   |  case _ =>
                   |} """.stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_8c() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (${START}x1.isInstanceOf[Long]$END && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = s"""val x1 = 0
                 |val x2 = 0
                 |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |} else if (<caret>x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x1 = 0
                   |val x2 = 0
                   |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                   |  val y1 = x1.asInstanceOf[Int]
                   |  val y2 = x2.asInstanceOf[Int]
                   |  println(y1 + y2)
                   |} else x1 match {
                   |  case _: Long if x2.isInstanceOf[Long] =>
                   |    val y1 = x1.asInstanceOf[Int]
                   |    val y2 = x2.asInstanceOf[Int]
                   |    println(y1 + y2)
                   |  case _ =>
                   |}
                   |""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }

  def test_9() {
    val selected = s"""val x = 0
                     |val i = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}""".stripMargin.replace("\r", "").trim
    check(selected)

    val text = """val x = 0
                 |val i = 0
                 |if (x.isInstanc<caret>eOf[Int]) {
                 |  x.asInstanceOf[Int].toString
                 |  println(x.asInstanceOf[Int])
                 |}""".stripMargin.replace("\r", "").trim
    val result = """val x = 0
                   |val i = 0
                   |x match {
                   |  case i1: Int =>
                   |    i1.toString
                   |    println(i1)
                   |  case _ =>
                   |}""".stripMargin.replace("\r", "").trim
    testFix(text, result)
  }
}

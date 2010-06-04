package org.jetbrains.plugins.scala.lang.resolve2



/**
 * Pavel.Fatin, 02.02.2010
 */

class BasicTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "basic/"
  }

  def testSimple = doTest
  def testMultipleDeclaration = doTest
  def testName = doTest
}
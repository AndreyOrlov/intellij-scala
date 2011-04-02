class CaseClauseInput {
  def foo {
    1 match {
      case x => {
        /*start*/
        x + 1
        x + 2
        /*end*/
      }
      case _ =>
    }
  }
}
/*
class CaseClauseInput {
  def testMethodName(x: Int) {
    x + 1
    x + 2
  }

  def foo {
    1 match {
      case x => {
        /*start*/
        testMethodName(x)
        /*end*/
      }
      case _ =>
    }
  }
}
*/
class CallByNameInput {
  def foo(x: => Int) {
    /*start*/
    x + 44
    /*end*/
  }
}
/*
class CallByNameInput {
  def testMethodName(x: => Int) {
    x + 44
  }

  def foo(x: => Int) {
    /*start*/
    testMethodName(x)
    /*end*/
  }
}
*/
class OneInput {
  def foo {
    val i = 34
    /*start*/
    i * i
    /*end*/
  }
}
/*
class OneInput {
  def testMethodName(i: Int) {
    i * i
  }

  def foo {
    val i = 34
    /*start*/
    testMethodName(i)
    /*end*/
  }
}
*/
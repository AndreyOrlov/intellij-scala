class FunTypeParam {
  def f[T](x: T) {
    val y = x
    /*start*/
    x
    y
    /*end*/
  }
}
/*
class FunTypeParam {
  def testMethodName[T](x: T, y: T) {
    x
    y
  }

  def f[T](x: T) {
    val y = x
    /*start*/
    testMethodName(x, y)
    /*end*/
  }
}

*/
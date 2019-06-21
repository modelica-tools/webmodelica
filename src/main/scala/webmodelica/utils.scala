package webmodelica

object utils {
  /** Returns only every n-th element of the given list.
    * source: https://stackoverflow.com/questions/25227475/list-of-every-n-th-item-in-a-given-list
    */
  def skip[A](l:List[A], n:Int) =
    l.zipWithIndex.collect {case (e,i) if ((i+1) % n) == 0 => e}
}

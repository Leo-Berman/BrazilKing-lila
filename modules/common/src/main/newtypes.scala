package lila.base

// thanks Anton!
// https://github.com/indoorvivants/sn-bindgen/blob/main/modules/bindgen/src/main/scala/newtypes.scala
trait NewTypes {

  trait BasicallyTheSame[A, T]:
    def apply(a: A): T

  type BasicallyString[A] = BasicallyTheSame[A, String]

  trait TotalWrapper[Newtype, Impl](using ev: Newtype =:= Impl):
    def raw(a: Newtype): Impl   = ev.apply(a)
    def apply(s: Impl): Newtype = ev.flip.apply(s)
    // given =:=[Newtype, Impl]              = ev
    given BasicallyTheSame[Newtype, Impl] = ev.apply(_)
    given BasicallyTheSame[Impl, Newtype] = ev.flip.apply(_)

    extension (a: Newtype)
      inline def value                                           = raw(a)
      inline def into[X](inline other: TotalWrapper[X, Impl]): X = other.apply(raw(a))
      inline def map(inline f: Impl => Impl): Newtype            = apply(f(raw(a)))
  end TotalWrapper

  trait OpaqueString[A](using A =:= String) extends TotalWrapper[A, String]
  trait OpaqueInt[A](using ev: A =:= Int) extends TotalWrapper[A, Int]:
    extension (a: A) inline def atMost(most: Int): A = apply(java.lang.Math.min(raw(a), most))

  import scala.concurrent.duration.FiniteDuration
  trait OpaqueDuration[A](using A =:= FiniteDuration) extends TotalWrapper[A, FiniteDuration]

  abstract class YesNo[A](using ev: Boolean =:= A):
    val Yes: A                             = ev.apply(true)
    val No: A                              = ev.apply(false)
    inline def apply(inline b: Boolean): A = ev.apply(b)
    given BasicallyTheSame[A, Boolean]     = _ == Yes
    given BasicallyTheSame[Boolean, A]     = if _ then Yes else No

    extension (inline a: A)
      inline def value: Boolean = a == Yes
      inline def yes: Boolean   = a == Yes
  end YesNo

  inline def sameOrdering[A, T](using bts: BasicallyTheSame[T, A], ord: Ordering[A]): Ordering[T] =
    Ordering.by(bts.apply)
  inline def stringOrdering[T](using BasicallyTheSame[T, String], Ordering[String]): Ordering[T] =
    sameOrdering[String, T]

  def stringIsString: BasicallyString[String] = new BasicallyString[String]:
    def apply(a: String) = a
}
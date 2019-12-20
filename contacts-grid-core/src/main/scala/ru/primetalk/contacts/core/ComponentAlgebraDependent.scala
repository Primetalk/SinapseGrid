package ru.primetalk.contacts.core

import UniSets._

import scala.annotation.tailrec

trait ComponentAlgebraDependentBase { base =>
  type ComponentShape[A<: UniSet, B<: UniSet] = (A,B)
  /** This is for user to implement/define.
    * User should create a component type-level identifier that
    * extends this type.
    */
  trait Component {
    // set of input contacts
    type In <: UniSet
    // set of output contacts
    type Out <: UniSet
  }

  /** One of the mechanisms to create new components is to put them in parallel. */
  sealed trait ParallelAdd[C1 <: Component, C2 <: Component] extends Component {
    type In = Union[C1#In, C2#In]
    type Out = Union[C1#Out, C2#Out]
    type Self = ParallelAdd[C1, C2]
  }

  def parallelAdd[C1 <: Component, C2 <: Component](c1: C1, c2: C2): ParallelAdd[C1, C2] =
    new ParallelAdd[C1, C2]{}

  /** A powerful mechanisms to compose components is to put them on the breadboard one by one.
    * and then at some moment produce a new component by projecting the breadboard on some inputs and outputs. */
  sealed trait Breadboard {
    type Sinks <: UniSet
    type Sources <: UniSet
  }

  object EmptyBreadboard extends Breadboard {
    type Sinks = Empty
    type Sources = Empty
    type Impl = base.ImplementationComponent[EmptyBreadboard]
    def withAddedComponent[C <: Component]: WithAddedComponent[C, EmptyBreadboard] =
      new WithAddedComponent[C, EmptyBreadboard] {}
    def toComponent[I <: UniSet, O <: UniSet](implicit i: IsSubSetOf[I, Empty]): ToComponent[I, O, EmptyBreadboard] =
      new ToComponent[I, O, EmptyBreadboard] {}

  }
  type EmptyBreadboard = EmptyBreadboard.type
  sealed trait WithAddedComponent[C <: Component, B <: Breadboard] extends Breadboard {
    type Sinks = Union[C#In, B#Sinks]
    type Sources = Union[C#Out, B#Sources]
    type Self = WithAddedComponent[C, B]
    type Impl = base.ImplementationComponent[Self]

    def withAddedComponent[C1 <: Component]: WithAddedComponent[C1, Self] =
      new WithAddedComponent[C1, Self] {}
    def toComponent[I1 <: UniSet, O1 <: UniSet](implicit i: IsSubSetOf[I1, Sinks]): ToComponent[I1, O1, Self] =
      new ToComponent[I1, O1, Self] {}

  }

  sealed trait ImplementationComponent[B <: Breadboard] extends Component {
    type In = B#Sinks
    type Out = B#Sources
    type Self = ImplementationComponent[B]
  }
  sealed trait ToComponent[I <: UniSet, O <: UniSet, B <: Breadboard] extends Component {
    type In = I
    type Out = O
    type Self = ToComponent[I, O, B]
  }

}

trait ComponentAlgebraDependentFeatures extends ComponentAlgebraDependentBase with Signals {

  sealed trait HandlerOf[C <: Component] {
    def handler: C#In >> C#Out
  }

}
trait HandlerOfsDependent extends ComponentAlgebraDependentFeatures {
  def defineHandlerOf[C <: Component](f: C#In >> C#Out): HandlerOf[C] = new HandlerOf[C] {
    override def handler: C#In >> C#Out = f
  }

  def convertHandlerOf[C1 <: Component, C2 <: Component]
  (implicit h: HandlerOf[C1], evin: IsSubSetOf[C2#In, C1#In],evout: IsSubSetOf[C1#Out, C2#Out]): HandlerOf[C2] = new HandlerOf[C2] {
    override def handler: C2#In >> C2#Out = s => {
      val s1 = s.cProjection[C1#In]
      val o1s = h.handler(s1)
      o1s.map(o1 => o1.cProjection[C2#Out])
    }
  }

  implicit def parallelAddHandlerOf[C1 <: Component, C2 <: Component]
  (implicit
   h1: HandlerOf[C1],
   h2: HandlerOf[C2],
   i1: Render[Contact, C1#In],
   i2: Render[Contact, C2#In],
   o: Render[Contact, Union[C1#Out, C2#Out]]
  ): HandlerOf[ParallelAdd[C1, C2]] =
    new HandlerOf[ParallelAdd[C1, C2]] {
      override def handler: Union[C1#In, C2#In] >> Union[C1#Out, C2#Out] = signal => {
        val s1 = signal.projection0[C1#In].toIterable
        val s2 = signal.projection0[C2#In].toIterable
        val out1: Iterable[Signal[C1#Out]] = s1.flatMap(a => h1.handler(a))
        val out2: Iterable[Signal[C2#Out]] = s2.flatMap(a => h2.handler(a))
        val res =
          out1.map(_.cProjection[Union[C1#Out, C2#Out]]) ++
            out2.map(_.cProjection[Union[C1#Out, C2#Out]])
        res
      }
    }

  implicit def emptyComponentHandlerOf[C<: Component{ type In = Empty; type Out = Empty }]: HandlerOf[C] =
    new HandlerOf[C] {
      override def handler: Empty >> Empty = s => throw new IllegalArgumentException(s"emptyComponentHandler.handler cannot get any input $s")
    }

  implicit def addComponentHandlerOf[B0 <: Breadboard, C <: Component]
  (implicit
   h1: HandlerOf[C],
   h2: HandlerOf[ImplementationComponent[B0]],
   i1: Render[Contact, C#In],
   i2: Render[Contact, B0#Sinks],
   o: Render[Contact, Union[C#Out, B0#Sources]]
  )
  :    HandlerOf[ImplementationComponent[WithAddedComponent[C, B0]]] =
  new HandlerOf[ImplementationComponent[WithAddedComponent[C, B0]]] {
          override def handler: Union[C#In, B0#Sinks] >> Union[C#Out, B0#Sources] = signal => {
            val s1 = signal.projection0[C#In].toIterable
            val s2 = signal.projection0[B0#Sinks].toIterable
            val out1: Iterable[Signal[C#Out]] = s1.flatMap(a => h1.handler(a))
            val out2: Iterable[Signal[B0#Sources]] = s2.flatMap(a => h2.handler(a))
            val res =
              out1.map(_.cProjection[Union[C#Out, B0#Sources]]) ++
                out2.map(_.cProjection[Union[C#Out, B0#Sources]])
            res
          }
        }

  implicit def toComponentHandlerOf[I <: UniSet, O <: UniSet, B <: Breadboard]
  (implicit
    bh: HandlerOf[ImplementationComponent[B]],
     inputs1: Render[Contact, I],
     outputs1: Render[Contact, O],
     i: IsSubSetOf[I, B#Sinks], //o: IsSubSetOf[O, Sources],
    nonOutputsIsSubsetOfInputs: IsSubSetOf[B#Sources, Union[B#Sinks, O]]
  )
  : HandlerOf[ToComponent[I, O, B]] = new HandlerOf[ToComponent[I, O, B]] {
    override def handler: I >> O = signal => {
      @tailrec
      def loop(innerInputSignals: Iterable[Signal[B#Sinks]], tempOutput: Iterable[Signal[O]]): Iterable[Signal[O]] = {
        if(innerInputSignals.isEmpty)
          tempOutput
        else {
          val innerResults: Iterable[Signal[B#Sources]] = innerInputSignals.flatMap(bh.handler)
          val sortedResults = innerResults.map{s => projection0EitherUnion[B#Sinks, O, B#Sources](s)}
          val lefts = sortedResults.flatMap(_.left.toOption)
          val rights = sortedResults.flatMap(_.toOption)
          val leftsAsInputs = lefts.map(_.cProjection[B#Sinks])
          loop(leftsAsInputs, tempOutput ++ rights)
        }
      }
      val inputSignal = signal.cProjection[B#Sinks](i)
      loop(Iterable.single(inputSignal), Iterable.empty)
    }
  }
}

trait ComponentAlgebraDependentDSL extends HandlerOfsDependent with MySignals { self =>

  class ForComponentImpl[I <: Contact, O <: Contact, C <: Component{ type In = Singleton[I]; type Out = Singleton[O]}](in: I, out: O, c: C) {
    def liftIterable[A >: I#Data, B <: O#Data](f: A => Iterable[B]): HandlerOf[C] =
      defineHandlerOf[C](self.liftIterable(in, out)(a => f(a)))
    def lift[A >: I#Data, B <: O#Data](f: A => B): HandlerOf[C] =
      defineHandlerOf[C](self.lift(in, out)(a => f(a)))
  }

  def forComponent[I <: Contact, O <: Contact, C <: Component{ type In = Singleton[I]; type Out = Singleton[O]}](in: I, out: O, c: C): ForComponentImpl[I, O, C] =
    new ForComponentImpl[I, O, C](in, out, c)

//  implicit class ComponentOps[C <: Component]
//  (c: C) {
//    def lift[A >: In#Data, B <: Out#Data](f: A => B): HandlerOf[Singleton[In], Singleton[Out], C] =
//      defineHandlerOf[Singleton[In], Singleton[Out], C](self.lift(valueOf[In], valueOf[Out])(a => f(a)))
//
//  }
}

trait ComponentAlgebraDependent extends ComponentAlgebraDependentDSL {

}
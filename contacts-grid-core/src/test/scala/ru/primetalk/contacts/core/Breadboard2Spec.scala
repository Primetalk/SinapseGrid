package ru.primetalk.contacts.core

import org.specs2.Specification
//import TypeSets._
import ru.primetalk.contacts.core.UniSets._

import scala.util.Try

class Breadboard2Spec extends Specification with ComponentAlgebra with MySignals with NamedContacts { def is = s2"""

  This is specification of Breadboard

    - inputs of the component should be known set inputsEq
  """

  sealed trait MyContact extends Contact

  abstract class ContactImpl[A](val name: String) extends Product with Serializable with MyContact {
    override type Data = A
  }

  type In1 = NamedContact["In1", String]
  val In1: In1 = NamedContact["In1", String]

  //  val in2 = contact["in2", Int]
//  val ev = implicitly[NamedContact["in1", Int] =:= NamedContact["in1", Int] ]
//  val ev = implicitly[in1.type =:= in2.type]
//  case object In1 extends ContactImpl[String]("In1")
  case object Out1 extends ContactImpl[Int]("Out1")

  case object In2 extends ContactImpl[Int]("In2")
  case object Out2 extends ContactImpl[String]("Out2")

//  val out2 = valueOf[Out2.type]
//  val out1 = valueOf[Out1.type]
  object Parser extends Component[Si[In1], Si[Out1.type]]
  object Shower extends Component[Si[In2.type], Si[Out2.type]]
  object Incrementer extends Component[Si[Out1.type], Si[In2.type]]

  // Sink   = In1, In2, Out1
  // Source = Out1, Out2, In2
  // I      = In1
  // O      = Out2
  // source in Union(O, Sink)
  val both: ParallelAdd[Si[In1], Si[Out1.type], Parser.type, Si[In2.type], Si[Out2.type], Shower.type] =
    parallelAdd(Parser, Shower)


  val bbParser = EmptyBreadboard.withAddedComponent[Si[In1], Si[Out1.type], Parser.type]
  type bbParserImpl = bbParser.Impl
  //  val bbParser = addComponentToBreadboard(emptyBreadboard, Parser)
  val bbParser_Shower = bbParser.withAddedComponent[Si[In2.type], Si[Out2.type], Shower.type]
  type bbParser_ShowerImpl = bbParser_Shower.Impl
  val bbParserIncrementerShower = bbParser_Shower.withAddedComponent[Si[Out1.type], Si[In2.type], Incrementer.type]
  type bbParserIncrementerShowerImpl = bbParserIncrementerShower.Impl
  val stringStringComponent = bbParserIncrementerShower.toComponent[Si[In1], Si[Out2.type]]

  type stringStringComponentType = stringStringComponent.Self


  def parse(s: String): Iterable[Int] = Try(s.toInt).toOption
  implicit val parserImpl = forComponent(In1, Out1, Parser).liftIterable(parse)
//: HandlerOf[UniSets.Singleton[NamedContact["In1", String]], UniSets.Singleton[Out1.type], Parser.type]
  //  In1.map(s => Try(s.toInt).toOption)
  def show(i: Int): String = i.toString
  implicit val showerImpl = forComponent(In2, Out2, Shower).lift(show)
//  implicit val showerImpl2 = //Shower.lift(show(_))
//    new ComponentOps[In2.type, Out2.type, Shower.type](Shower).lift(show)

  def inc(i: Int): Int = i + 1
  implicit val incrementerImpl = forComponent(Out1, In2, Incrementer).lift(inc)


  val bothImpl = implicitly[HandlerOf[Union[Si[In1], Si[In2.type]], Union[Si[Out1.type], Si[Out2.type]],
    ParallelAdd[Si[In1], Si[Out1.type], Parser.type, Si[In2.type], Si[Out2.type], Shower.type]]]

  val EmptyBreadboardHandler = implicitly[HandlerOf[Empty, Empty, ImplementationComponent[Empty, Empty, EmptyBreadboard]]]
  val parAddH = parallelAddHandlerOf[
    Si[In1], Si[Out1.type], Parser.type,
    Empty, Empty, ImplementationComponent[Empty, Empty, EmptyBreadboard]]
  val BBParserHandler =  implicitly[HandlerOf[
    Union[Si[In1], Empty], Union[Si[Out1.type],Empty], bbParser.Impl]]
  val BBParser_ShowerHandler =  implicitly[HandlerOf[
    Union[Si[In2.type], Union[Si[In1], Empty]], Union[Si[Out2.type],Union[Si[Out1.type],Empty]], bbParser_Shower.Impl]]

  val bbHandler = implicitly[HandlerOf[
    Union[Si[Out1.type], Union[Si[In2.type],Union[Si[In1],Empty]]],
    Union[Si[In2.type], Union[Si[Out2.type],Union[Si[Out1.type],Empty]]],
    bbParserIncrementerShower.Impl]]

  val tco = toComponentHandlerOf[
        Union[Si[Out1.type], Union[Si[In2.type],Union[Si[In1],Empty]]],
        Union[Si[In2.type], Union[Si[Out2.type],Union[Si[Out1.type],Empty]]],
        bbParserIncrementerShower.Self,  Si[In1], Si[Out2.type]]

  val stringStringImpl = implicitly[HandlerOf[Si[In1], Si[Out2.type], stringStringComponentType]]//(tco)

//  (
//    toComponentHandlerOf[
//      bbParserIncrementerShower.Sinks0,
//      bbParserIncrementerShower.Sources0 ,
//      bbParserIncrementerShower.type,  Si[In1], Si[Out2.type]])

//  val both = parallelAddComponent(Parser, Shower)
//
//  def printer(s: String): Unit = println("printer: " + s)
//  val liftedPrinter: Si[In1] >> Empty = liftI1(In1)(printer)
//  val Printer = createComponent(shapePrinter)(liftedPrinter)
//
  val inputSignal1: SignalOnContact {
    type C = In1
  } =  SignalOnContact.create[In1]("10")
  val inputSignal =  signal[Si[In1]](inputSignal1)
  val res = stringStringImpl.handler(inputSignal)

  res.flatMap(_.unwrap(Out2)).foreach { int =>
    println(int)
  }
//
//  val threeComponents = parallelAddComponent(both, Printer)
//
//  val res3 = threeComponents.handler(signal[threeComponents.shape.InputShape](inputSignal1))
//
//  res3.flatMap(_.unwrap(Out1)).foreach { int =>
//    println("Output: " + int)
//  }
//  val bb1 = addComponentToBreadboard(emptyBreadboard, Printer)
//
//  // composing In1 -> parse -> Out1 -> inc ->In2 -> show -> Out2 into a single component String->String
//
//
//  def inc(i: Int): Int = i + 1
//  val bbParserIncShow: Breadboard[BreadboardShape {
//    type SourceShape = Union[Union[Union[Empty, Singleton[Out1.type]], Singleton[Out2.type]], Singleton[In2.type]]
//
//    type SinkShape = Union[Union[Union[Empty, Singleton[In1]], Singleton[In2.type]], Singleton[Out1.type]]
//  }] = addComponentToBreadboard(bbParser_Show, Incrementer)
//
////  val ev = implicitly[
////      Union[Singleton[Out1.type], Singleton[In2.type]]
////     IsSubSetOf
////      Union[Union[Singleton[In1], Singleton[In2.type]], Singleton[Out1.type]]]
////val ev = implicitly[
////    Union[Union[Empty, Singleton[Out1.type]], Singleton[In2.type]]
////    IsSubSetOf
////    Union[Union[Union[Empty, Singleton[In1]], Singleton[In2.type]], Singleton[Out1.type]]]
////  val ev = implicitly[
////    Subtract[
////      Union[Union[Union[Empty, Singleton[Out1.type]], Singleton[Out2.type]], Singleton[In2.type]],
////      Singleton[Out2.type]
////    ] IsSubSetOf
////      Union[Union[Union[Empty, Singleton[In1]], Singleton[In2.type]], Singleton[Out1.type]]]
//
////    bbParserIncShow.toComponent[Si[In1], Si[Out2.type]]
//  val componentStringString: Component[ComponentShape {
//    type InputShape = Si[In1]
//    type OutputShape = Si[Out2.type]
//  }] = bbParserIncShow.toComponent[Si[In1], Si[Out2.type]]
//
//  val res11 = componentStringString.handler(signal[componentStringString.shape.InputShape](inputSignal1))
//  res11.flatMap(_.unwrap(Out2)).foreach { str =>
//    println("Incremented number: " + str)
//  }

  /*
  val topLevelComponent: MyComponent[Shape] - no implementation
  implement def handler = {
    val b = createBreadboard
    b.add(comp1)(implicit comp1Implementation)
  }
   */
}
package ru.primetalk.synapse.core

import org.junit.Test
import syntax._
import syntax.given

class FlowTest {
  /** This example is adapted from akka-streams documentation. */
  object FlowSystem extends BaseTypedSystem("FlowSystem"){
    val in: Contact[Int] = input[Int]("in")
    val out: Contact[Int] = output[Int]("out")

    override protected def defineSystem(implicit sb: SystemBuilder): Unit = {
      val bcast = contact[Int]("bcast")
      val merge = contact[Int]("merge")

      val f1, f2, f3, f4 = (i: Int) => i + 10

      LinkBuilderOps(in -> bcast).map(f1, "f1")
      LinkBuilderOps(bcast -> merge).map(f2, "f2")
      LinkBuilderOps(bcast -> merge).map(f4, "f4")

      LinkBuilderOps(merge -> out).map(f3, "f3")
    }
    def f: Int => IterableOnce[Int] = this.toDynamicSystem.toTransducer(in, out)
  }
  
  @Test def FlowSystemTest(): Unit = 
    assert(FlowSystem.f(0) == List(30, 30))
    val res = (1 to 3).flatMap(FlowSystem.f)
    assert(res == Vector(31, 31, 32, 32, 33, 33), s"res = $res")
  
  @Test def FlowSystemToDot(): Unit =
    FlowSystem.toStaticSystem.toDot(2).saveTo("FlowSystem.dot")
}

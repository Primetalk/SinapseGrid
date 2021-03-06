package ru.primetalk.synapse.akka.impl

import akka.actor.{SupervisorStrategy, ActorRef, ActorRefFactory}
import ru.primetalk.synapse.akka._
import ru.primetalk.synapse.core.components.StaticSystem
import ru.primetalk.synapse.core.EncapsulationBuilder
import ru.primetalk.synapse.core.SystemBuilder

/**
 * @author zhizhelev, 25.03.15.
 */
trait StaticSystemAkkaApi {
  implicit class RichStaticSystemSystem[T](s: T)(implicit ev:T => StaticSystem) {
    /**
     * The main DSL function to convert a StaticSystem to an actor tree.
     * Returns top level actor.
     * @param threadSafeOutputFun - a function that will receive output signals of the actor.
     *                            Should be thread safe!!!
     *                            May be omitted. In the latter case the output signals
     *                            are ignored.*/
    def toActorTree(threadSafeOutputFun: Option[InternalSignalsDist => Any] = None)
                   (implicit actorRefFactory: ActorRefFactory): ActorRef =
      StaticSystemActor.toActorTree(actorRefFactory)(List(), s, threadSafeOutputFun)

    @deprecated("use #toTopLevelActor", "26.03.2015")
    def toActorTree(implicit actorRefFactory: ActorRefFactory): ActorRef =
      toActorTree(None)(actorRefFactory)

    def toTopLevelActor(implicit actorRefFactory: ActorRefFactory): ActorRef =
      StaticSystemActor.toActorTree(actorRefFactory)(List(), s, None)


    /** usage:
      *
      * addComponent(someStaticSystem.toActorComponent)
      */
    def toActorComponent(supervisorStrategy: SupervisorStrategy = defaultSupervisorStrategy) =
      new ActorComponent(s, supervisorStrategy)

  }
  /** Encapsulates the given outer interface and adds the implementation as an ActorComponent
    * to the current SystemBuilder*/
  def encapsulateAsActor[Outer](en:EncapsulationBuilder[Outer], supervisorStrategy: SupervisorStrategy = defaultSupervisorStrategy)(implicit sb:SystemBuilder):Outer = {
    sb.addComponent(new ActorComponent(en.toStaticSystem, supervisorStrategy))
    en.outer
  }


}

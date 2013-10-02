///////////////////////////////////////////////////////////////
// © ООО «Праймтолк», 2011-2013                              //
// Все права принадлежат компании ООО «Праймтолк».           //
///////////////////////////////////////////////////////////////
/**
 * SynapseGrid
 * © Primetalk Ltd., 2013.
 * All rights reserved.
 * Authors: A.Zhizhelev, A.Nehaev, P. Popov
 *
 * Created: 21.09.13, zhizhelev
 */
package ru.primetalk.synapse.concurrent

import ru.primetalk.synapse.core._
import scala.concurrent.{Await, Future, ExecutionContext}
import scala.language.existentials
import ru.primetalk.synapse.core.Signal
import ru.primetalk.synapse.core.RuntimeComponentStateFlatMap
import ru.primetalk.synapse.core.DynamicSystem
import ru.primetalk.synapse.core.RuntimeComponentFlatMap
import ru.primetalk.synapse.core.RuntimeComponentMultiState
import ru.primetalk.synapse.core.RuntimeSystem
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.collection.mutable


/** The prerequisites for the UnitOfComputation
  * Requires that the states have got their values for AtTime moment.
  * The computation unit will softly block over this state. */
case class StateRequirement(stateHandles: List[Contact[_]], time:HTime) //extends Requirement


/**
 * The unit of computation may have constraints:
 * - list of states @time
 *
 * Also it can have properties
 * - update state@time
 *
 * Units are interconnected with dependencies:
 * - incoming units of computation
 * - dependent units of computation
 *
 * interconnection is given outside
 */
case class UnitOfComputation(signalAtTime: AtTime[Signal[_]], // the signal that triggered the computation.
                             rc: RuntimeComponent){
  val stateRequirement = rc match {
    case RuntimeComponentFlatMap(name, _, _, f) =>
      StateRequirement(List(),signalAtTime.time)
    case RuntimeComponentStateFlatMap(name, _, _, sh, f) =>
      StateRequirement(List(sh), signalAtTime.time)
    case RuntimeComponentMultiState(name, stateHandles, f) =>
      StateRequirement(stateHandles, signalAtTime.time)
  }
}
case class ComputationCompleted(
                                 computation:UnitOfComputation,
                                 newSignals:List[AtTime[Signal[_]]],
                                 statesToRelease:List[AtTime[Contact[_]]])

case class RunningUnitOfComputation(u: UnitOfComputation, future: Future[ComputationCompleted])



/** A shared state that is used by planner between threads.
  *
  * Computational units and time moments are connected
  * with Happens-before primitive
  *
  * If a function has side effects it should declare the dependency on
  * a state handle "GlobalState". Then the planner will avoid running
  * the function in parallel with other side-effect functions. All other
  * simple functions are considered as side-effect free.
  *
  * The signals are promoted to have `time moment` in order to ensure
  * trellis properties.
  *
  */
class ComputationState(rs: RuntimeSystem,
                       state0:Map[Contact[_], _])(implicit val executionContext: ExecutionContext) {
  // whenever a signal appear, we create a plan
  private var runningCalculations = Vector[RunningUnitOfComputation]()
  private var computationQueue = mutable.Queue[UnitOfComputation]()



  private[concurrent] val variables:Map[Contact[_], SafeVariable[_]] =
    state0.map{ case (sh, initialValue) => (sh, new SafeVariable(initialValue))}.toMap


  /** Absolute past. No signal can appear before this time. */
  private var pastTimeBoundary = 0
  private var outputs = List[AtTime[Signal[_]]]()

  /** Changes context to the given value*/
  def resetContext(context:Context){
    this.synchronized{
      for{sh <- state0.keys}{
        val v = variables(sh).asInstanceOf[SafeVariable[Any]]
        v.update(old => context(sh))
      }
    }
  }
  def getContext:Context =
    this.synchronized{
      state0.keys.
        map(sh =>
          (sh,
            variables(sh).asInstanceOf[SafeVariable[Any]].get)).
        toMap
    }

  /** Add a single signal.
    *
    * Starts immediate computations if requirements are met.
    * Adds them to running. Those computations that cannot be started immediately
    * will be put to the computation plan. */
  def addSignal(signalAtTime: AtTime[Signal[_]]) {
    val signal = signalAtTime.value
    val contact = signal.contact
    if (rs.stopContacts.contains(contact))
      this.synchronized {
        outputs = signalAtTime :: outputs
      }
    else {
      val components = rs.signalProcessors(contact)
      val computationUnits = components.zipWithIndex.map{ case (c, i) =>
        val signalForComponent = //AtTime.placeAfter(signalAtTime.time,  signalAtTime.value)//
         signalAtTime.copy(time = signalAtTime.time.next(i))
        UnitOfComputation(signalForComponent, c)
      }
      this.synchronized {
        computationUnits.foreach(computationQueue.enqueue(_))
        fastCheckPlan()
      }
    }
  }

  /** Simple check. It can also search through graph and allow look ahead state-based
    * calculations.*/
  private
  def checkRequirement(stateRequirement: StateRequirement): Boolean = {
    stateRequirement.stateHandles.isEmpty ||(
      stateRequirement.time.trellisTime == pastTimeBoundary &&
      stateRequirement.stateHandles.forall( variables(_).lock.available)
    )
  }

  private
  def checkTaskAndStartIfPossible(t:UnitOfComputation) = {
    val starting = checkRequirement(t.stateRequirement)
    if(starting)
      start(t)
    starting
  }
  private
  def start(t: UnitOfComputation) {
    this.synchronized{
      import t._
      t.stateRequirement.stateHandles.foreach{stateHandle=>
        variables(stateHandle).lock.acquire()
      }
      val signal = signalAtTime.value
      val time = signalAtTime.time
//      def threadPrint() {println(Thread.currentThread().getName+": "+t)}
      val future = rc match {
        case RuntimeComponentFlatMap(name, _, _, f) =>
          Future {
            val signals = f(signal)
            ComputationCompleted(t, AtTime.placeAfter(time, signals), List())
          }
        case RuntimeComponentStateFlatMap(name, _, _, sh, f) =>
          val v = variables(sh).asInstanceOf[SafeVariable[Any]]
          val f2 = f.asInstanceOf[((Any, Signal[_]) => (Any, List[Signal[_]]))] //(state, signal)=>(state, signals)
          Future {
            val signals = v.update2 {
              oldValue => f2(oldValue, signal)
            }
            ComputationCompleted(t, AtTime.placeAfter(time, signals), List(AtTime(signalAtTime.time.next(-1), sh)))
          }
        case RuntimeComponentMultiState(name, stateHandles, f) =>
          val f2 = f.asInstanceOf[((Context, Signal[_]) => (Context, List[Signal[_]]))] //(state, signal)=>(state, signals)
          Future {
            val context = stateHandles.map(sh =>
              (sh.asInstanceOf[Contact[Any]], variables(sh).get)).toMap.asInstanceOf[Context]//[Contact[_], _]
            val (newContext, signals) = f2(context, signal)
            for((sh, v) <- newContext.asInstanceOf[Context])
              variables(sh).asInstanceOf[SafeVariable[Any]].update(t => v)
            ComputationCompleted(t, AtTime.placeAfter(time, signals), AtTime.placeAfter(time, stateHandles))
          }
      }
      future.onSuccess {
        case cc => computationCompleted(cc)
      }
      runningCalculations = RunningUnitOfComputation(t, future) +: runningCalculations
    }
  }

  private
  def computationCompleted(computationCompleted:ComputationCompleted) {
    import computationCompleted._
    this.synchronized{ // TODO O(n)->O(1) (map)
      runningCalculations = runningCalculations.filterNot(_.u eq computation)
      statesToRelease.foreach(stateHandleAtTime=>variables(stateHandleAtTime.value).lock.release())
      newSignals.foreach(addSignal)
    }
  }
  /**
    Runs computations for which requirements are met.

    During execution we select earlier signals that can be
    processed without violating computation constraints.

    The planner plans simple sequences that can be calculated in a row.

    The planning is performed whenever an executor has
    nothing to do independently. Before planning next steps the independently
    generated trellis is merged into the trunk one.

    Every time when planning is performed, signals that can be processed
    independently are removed from trellis and corresponding tasks are created.

    For links that depend on a state the state is moved to a soft lock list
    with the time moment.
    The state processing is not started until all previous time moments
    have been handled.

   */
  private
  def checkPlan() {
    this.synchronized {
      computationQueue = computationQueue.sortBy(_.signalAtTime.time)
      runningCalculations = runningCalculations.sortBy(_.u.signalAtTime.time)
      val times = Int.MaxValue ::
        runningCalculations.headOption.toList.map(_.u.signalAtTime.time.trellisTime) :::
          computationQueue.headOption.toList.map(_.signalAtTime.time.trellisTime)
      pastTimeBoundary = times.min
      fastCheckPlan()
    }
  }

  /** Do not sort computations and update past time boundary. Just tries to find easy jobs.*/
  private
  def fastCheckPlan() {
    this.synchronized {
      val delayed = mutable.Queue[UnitOfComputation]()
      var alreadyRequiredStates = runningCalculations.flatMap(_.u.stateRequirement.stateHandles).toSet//Set[Contact[_]]()
      computationQueue = computationQueue.sortBy(_.signalAtTime.time)
      for{unit <- computationQueue}{// the queue is sorted accordingly
        val stateRequirement = unit.stateRequirement
        if(stateRequirement.stateHandles.isEmpty)
          start(unit)
        else{
          if(stateRequirement.time.trellisTime != pastTimeBoundary)
            delayed.enqueue(unit)
          else{
             if(
                stateRequirement.stateHandles.forall(sh =>
                  !alreadyRequiredStates.contains(sh) &&
                  variables(sh).lock.available)
              )
              start(unit)
            else{
              alreadyRequiredStates = alreadyRequiredStates ++ stateRequirement.stateHandles
              delayed.enqueue(unit)
             }
          }
        }
      }
      computationQueue = delayed
    }
  }


  private
  def waitUntilCompleted(atMostPerAwaitableFuture:Duration = Duration.Inf) {
    @tailrec
    def waitUntilCompleted0():Unit = {
      val head = this.synchronized(runningCalculations.headOption)
      if(head.isEmpty){
        if(this.synchronized(computationQueue.isEmpty))
          ()// finished
        else {
          checkPlan()
          waitUntilCompleted0()
        }
      }else
      {
        Await.result(head.get.future, atMostPerAwaitableFuture)
        waitUntilCompleted0()
      }
    }
    waitUntilCompleted0()
  }

  def runUntilAllOutputSignals:List[Signal[_]] = {
    waitUntilCompleted()
    outputs.sorted.map(_.value)
  }
}


object ComputationState {
  def runtimeSystemToTotalTrellisProducer(implicit ec:ExecutionContext) =
    (rs:RuntimeSystem) =>
    (context:Context, signal:Signal[_]) => {
      val cs = new ComputationState(rs, context)
      cs.addSignal(AtTime(HTime(None, 0), signal))
      val signals = cs.runUntilAllOutputSignals
      val newContext = cs.getContext
      (newContext, signals)
    }

  implicit class RichStaticSystem(system:StaticSystem)(implicit ec:ExecutionContext) {
    /** Converts a system to a parallel RuntimeSystem.
      * Actually, converts inner subsystems to ParallelRuntimeSystem.
      */
    def toParallelRuntimeSystem:RuntimeSystem = {
      SystemConverting.toRuntimeSystem(system, system.outputContacts, runtimeSystemToTotalTrellisProducer)
    }

    def toParallelSimpleSignalProcessor:SimpleSignalProcessor =
      runtimeSystemToTotalTrellisProducer(ec)(toParallelRuntimeSystem).toSimpleSignalProcessor(system.s0)
    /**
     * The resulting Dynamic system will work in background. However,
     * it will block until all computations have been completed.
     */
    def toParallelDynamicSystem:DynamicSystem =
      DynamicSystem(system.inputContacts, system.outputContacts, system.name,
        toParallelSimpleSignalProcessor)

  }

}
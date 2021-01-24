package ru.primetalk.synapse.core.runtime

import ru.primetalk.synapse.core.components.{Contact0, Signal0}
import ru.primetalk.synapse.core.dsl.SignalsApi
import ru.primetalk.synapse.core.ext.{ContactsIndexExt, ExceptionHandlingExt}

/**
 * @author zhizhelev, 05.04.15.
 */
trait RuntimeSystemApi
  extends SignalsApi
  with ExceptionHandlingExt
  with RuntimeComponentApi with TrellisApi with ContactsIndexExt {

  /** A dictionary of handlers for signals that appear on contacts.*/
  type ContactToSubscribersMap = Map[Contact0, List[RuntimeComponent]]
  /** This contact is used to enable special simultaneous processing of signals.
    * For instance the contact can be used for debug purposes.
    * */
  object TrellisContact extends Contact[SignalCollection[Signal0]]
  /** A runtime system is a representation of the system that is
    * reorganized by Contacts and is ready for direct processing of TrellisElement. */
  case class RuntimeSystem(name: String,
                           signalProcessors: ContactToSubscribersMap,
                           stopContacts: Set[Contact0],
                           unhandledExceptionHandler: UnhandledProcessingExceptionHandler
                           = defaultUnhandledExceptionHandler
                            ) {
    lazy val contacts: Set[Contact0] = signalProcessors.keySet
    lazy val isTrellisContactUsed: Boolean = contacts.contains(TrellisContact)
  }

  /** Dynamic system. The state is kept inside the system. All complex logic
    * is implemented within receive function.
    * Dynamic system can be added to StaticSystem as a simple component ("black box").
    * The processing of the dynamic system is done within a single step of
    * the outer system processor.
    */
  case class DynamicSystem(
                            inputContacts: Set[Contact0],
                            outputContacts: Set[Contact0],
                            name: String,
                            receive: SimpleSignalProcessor,
                            index: ContactsIndex) extends Named with Component with SimpleSignalProcessor{
    def apply(s: Signal0): IterableOnce[Signal0] = receive(s)
  }

  type RuntimeSystemToTotalTrellisProducerConverter = RuntimeSystem => TotalTrellisProducer

  implicit class RichDynamicSystem(system: DynamicSystem) {

    def toTransducer[TInput, TOutput](input: Contact[TInput], output: Contact[TOutput]): TInput => SignalCollection[TOutput] =
      new RichSimpleSignalProcessor(system.receive).toTransducer(input, output)

    def toMapTransducer[TInput, TOutput](input: Contact[TInput], output: Contact[TOutput]): TInput => TOutput =
      new RichSimpleSignalProcessor(system.receive).toMapTransducer(input, output)

    def toBuffered = new DynamicSystemBuffered(system)

  }
  /** A class that allows to use Dynamic system in a more comfortable way.
    * One can send any data on any input of the dynamic system and
    * the results are kept in output buffer.
    * Occasionally one may read output signals (clearing them out if neccessary).
    */
  class DynamicSystemBuffered(dynamicSystem:DynamicSystem) {
    private val outputBuffer = scala.collection.mutable.ListBuffer[Signal0]()
    def send[T](input:Contact[T])(data:T): DynamicSystemBuffered = {
      val inputSignal = Signal(input, data)
      val outputSignals = dynamicSystem.receive(inputSignal)
      outputBuffer ++= outputSignals
      this
    }
    def clear():Seq[Signal0] = {
      val result = outputBuffer.toSeq
      outputBuffer.clear()
      result
    }
    def read[T](output:Contact[T]):Seq[T] =
      outputBuffer.toSeq.get(output)

    /** Removes signals that corresponds to the given contact
      * @return data from removed signals */
    def remove[T](output: Contact[T]):Seq[T] = {
      val (res, rest) = outputBuffer.toSeq.partition(output)
      outputBuffer.clear()
      outputBuffer ++= rest
      res.map(_.data0).asInstanceOf[Seq[T]]
    }

  }

}

SinapseGrid contact system
=============================

Key features
--------------------
1. SinapseGrid provides better opportunity for function composition, which goes far away from monad capabilities.
2. By using mutually with Akka-actors, you have a opportunity to use strictly typed data processing, that significantly shifts Typed actors.
3. Composite functions, that have multiple input and output.

(Reasons, that brought us to creating SinapseGrid could be found here: [Потребности систем ведения диалога](docs/SpeechPortalMotivation.RU.md). )

Breadboard concept
------------------------
Contact System based on some principles. We will go through them in this section

Imagine a breadboard for assembling an electronic circuits.
There are bunch of apertures and contact areas. Some contacts are signed and arranged in the most comfortable way for electronic device connection
Other contacts dedicated for service purposes, and used to link components

There are bunch of different components, installed on breadboard, that forming, for instance: power supply, low frequency amplifier, some filter or anything else.
Some components may stay untapped, some contacts and subsystems may not be involved.
There'll be no components connected to them. Some power supply outputs may stay unclaimed. Or some circuit's inputs may be unused.

Breadboard can be a good illustration to contact system.


Contacts and impedors
------------------------
The Contact is called an object with Contact[T] type, with a name, that usually matches variable name, which stores it's link.
Example is listed below ( All examples are using Scala )

<pre>
	val myContact = contact[String]("myContact")
</pre>

Contact that doesn't contain any data. It designates a point on the breadboard. It may have a String typed components i/o components.
The component that has one input, and one output is called an arrow or impedor.
In the most simple and common way, an ordinary function can be applied as component.

<pre>
	def getLength(s:String) = s.length
</pre>

Let's code construction, which will calculate length for every incoming sting

<pre>
	val len = contact[Int]("len")
	myContact -> len map getLength
</pre>

or, more briefly

<pre>
	val len = myContact.map(_.length)
</pre>

Th sample of system is listed below ( to make testing possible, additional contacts were connected: input, output ).

![example1 system picture](images/example1.png)

( In the last case, the len contact of following type (Int) will be created automatically. )


Data processing
------------------------------------
The code above, doesn't make any kind of job. Contacts don't store any data, functions either.
This code only declares system structure - Contacts and their bounds with components.

External binding - used to attach data with some contact. I.e. Object, that contains contact line
I.e. Will be created a object, that contains link, contact and data, which bound to this contact.
In Concat System terminology this object will be called a Signal.

<pre>
	case class Signal[T](contact:Contact[T], data:T)
</pre>

(Besides of Signal you may meet terms like: Event, Data, Frame and Message.)
System state will be represented as a Signal's list on different contacts in one discrete time moment.

<pre>
	type Signals = List[Signal[_]]
</pre>

There is a special implemented component SignalProcessor, which performs functional transformation of the original list of signals at a time to the alarm list
later time.
Each signal is transmitted to the input queue of each component connected to the corresponding contact.
The output waveform component (or more signals) is added to the list of the next timing signal.
Signal Processor exits, when all signals are processed by previous points of time

The theory of hidden Markov models has a good notion of trellis ( the time scan of signal constellation).
SignalProcessor is used to build trellis, that based on the input data.

When the trellis building stops?
If the process does not stop, then all data will reach the outer contacts and as there are no connected components all data will be lost.
To avoid this, output contacts are specified in system description.

<pre>
	outputs(len) // outputs(output1, output2, output3)
</pre>

therefore, precessing will stop, when all signals in the current list will belong to output contacts.


Arrow types
------------------------------------

The most common situation, in signal processing, when 0 or more input elements generated per one element
Handling such a situation in Scala language, could be performed via higher-order function flatMap.
That's why the arrows, annotated by functions, in the contact system,
That's why, the arrows in the contact system, annotated by functions and which return 0 .. n elements, has the FlatMap type.

<pre>
	val wordsContact = someStringContact.flatMap(_.split("\\s+".r))
</pre>

System will look like this:

![example2 system picture](images/example2.png)

An important case of FlatMap arrows are 0 .. 1 arrows, which reflect (or not) data, that depends on certain conditions.
There's also special method, dedicated for arrows creation — filter:

<pre>
	val nonEmptyString = myContact.filter(_.length>0)
</pre>


For-comprehension compatibility
-------------------------------

An interesting feature of the Scala is ability to use syntactic sugar for custom methods.
Particularly, methods as map, flatMap, filter and withFilter, are already announced so, there's possible to use a for-comprehension:

<pre>
	val helloContact = for {
	   s <- myContact
	   if s.length >0
	} yield "Hello, "+s
</pre>

The same code is listed below ( iе contains two arrows ):

<pre>
	val helloContact = myContact.filter(s => s.length>0).map(s=>"Hello, "+s)
</pre>

In some cases, when processing algorithm branches a lot, this syntax looks pretty good.

Working with state
-------------------
До сих пор все примеры оперировали только данными, приходящими на входной контакт. Результат нигде не сохранялся и передавался далее.
То есть использовались "чистые" функции без побочных эффектов — immutable. Такие функции обладают массой полезных свойств.
Например, легко распараллелить обработку на несколько потоков. Не требуется пересоздавать систему для обработки других данных — достаточно один раз при старте приложения её создать.
Отладка таких систем практически исключена за ненадобностью — из-за отсутствия внутреннего состояния и побочных эффектов результат всегда детерминированно определяется входными данными.

Если логика обработки данных требует сохранения состояния, то первое, что приходит в голову — использовать внутри функции переменную и сохранять состояние в ней. К примеру, так:

<pre>
	var counter = 0
	val helloCount = myContact.map({any => 	counter += 1;  counter})
</pre>

Этот способ будет работать, но, к сожалению, мы теряем все преимущества immutable системы.

А что, если хранить состояние отдельно от системы? И в нужный момент перед работой функции текущее состояние извлекается, а потом помещается обратно.

Как работать с таким состоянием, которое где-то хранится? Функция должна принимать на вход текущее значение состояния и возвращать новое значение.

<pre>
	val helloCount = myContact.[указание на переменную, где хранится состояние counter].map({(any, counter) => (counter+1, counter + 1)})
</pre>

Let's take a closer look to this function. We'll make id verbose via def;

<pre>
	def incCounter(any:String, counter:Int) : (Int, Int) = {
	  val newCounterValue = counter+1
	  val resultOfThisFunction = newCounterValue
	  return (resultOfThisFunction, newCounterValue)
	}
</pre>

The function, that process the state is pure. Q.e.d.

Остаётся только определиться, как нам ловко хранить и извлекать состояние.

Для идентификации различных переменных состояния мы будем использовать разновидность контакта — StateHandle[T].

<pre>
	val counterS = state[Int]("counterS", 0)
	val helloCount = contact[Int]("helloCount")
</pre>

This identifier contains variable type, name, and initial value.

Current state value is not available at update. Actually it's not stored anywhere.
(Забегая немного вперёд: SignalProcessor хранит текущие значения всех переменных состояния в Map'е).

Чтобы в нашей функции helloCounter использовать это состояние, необходимо на него сослаться:

<pre>
    (myContact.withState(counterS) -> helloCount).stateMap({(counter: Int, any:String) => (counter + 1, counter + 1)},"inc "+counterS)
	val helloCount = myContact.stateMap(counterS, {(any, counter) => (counter+1, counter + 1)})
</pre>

В итоге получилось несколько громоздко, но зато мы имеем все преимущества чистых функций.

![example3 system picture][example3]

[example3]: images/example3.png "System example #3"

DSL has a set of auxiliary high-order functions, that simplify working with states.
////////////////////////////////////////////////////////////////////////////////////////

Drawing system scheme
-----------------------

Since we have a declarative described system, we have a chance to study and analyse it.
Particularly, it will be very cosy to have a system graph.

To get system's image, toDot call will be sufficient.
This method traverses all system elements (contacts, arrows, subsystems) and generates a .dot text file.

Drawings in images folder pefromed
Просмотреть такой файл можно с помощью, например, программы XDot. Рисунки в папке images получены с помощью команды

<pre>
    dot -Tpng example3.dot > example3.png
</pre>


Конструирование системы с помощью Builder'ов
--------------------------------------------
Все примеры создания контактов и стрелочек должны находиться в каком-нибудь классе/трейте, унаследованном от SystemBuilder.
Именно в нём находятся основные методы, позволяющие инкрементно создавать контакты и разнообразные стрелочки.
Сам SystemBuilder, как подсказывает его название, является mutable классом и не участвует непосредственно в runtime-обработке.
Чтобы получить чистое описание системы, построенное Builder'ом, достаточно вызвать метод toStaticSystem.
Этот метод возвращает простой immutable case-класс, содержащий все контакты и стрелочки.

There are bunch of DLS's that stored in separate traits, to use them, you have to connect them to your Builder.

Для конструирования системы кроме очевидного способа

<pre>
	val sb = new SystemBuilderC("MySystem")
	import sb._
	...
	val system = sb.toStaticSystem
</pre>

you could also extend a trait

<pre>
	trait MySystemBuilder extends SystemBuilder {
	  // setSystemName("MySystem") 
	  ...
	}

	val system = new MySystemBuilder.toStaticSystem
</pre>

После получения StaticSystem, её можно непосредственно использовать в SignalProcessor'е для обработки сигналов.
При этом состояние системы придётся всё время передавать на вход SignalProcessor'у и запоминать при возврате.
Чтобы упростить управление состоянием, имеется специальный класс DynamicSystem = StaticSystem + State.
Пользоваться таким классом можно как обычной функцией (правда, имея в виду, что внутри спрятано состояние и функция имеет побочный эффект).


Subsystems
----------
По мере увеличения программы, написанной на контактах, возникает необходимость выделения блоков в подсистемы для целей повторного использования.
Чтобы добавить подсистему, используется метод addSubsystem.
Так как у подсистемы имеется своё состояние, то также указывается stateHandle, где будет хранится состояние.

<pre>
	val subsystem = new MySubsystemBuilder.toStaticSystem
	val s1 = state[SystemState]("s1", subsystem.s0)
	sb.addSubsystem(subsystem, s1)
</pre>

To make subsystem able to get an input data, some of it's contacts must be declared as input:

<pre>
	inputs(input1, input2)
</pre>

in this case, all data that appears in external system, on appropriate will be processed by subsystem.

If you have a need to connect a several instances of subsystem, you would like to bind them to different input/output contacts.
Для этого используются подсистема, вложенная в подсистему. В промежуточной подсистеме увязываются входы со входами и выходы с выходами.
Для этого в builder'е промежуточной подсистемы используются методы mappedInput, mappedOutput, inputMappedTo, mapToOutput.
Эти методы обеспечивают создание wiring'ов, обеспечивающих связи между контактами внешней подсистемы и контактами внутренней подсистемы.


Akka Actors usage
---------------------------
All systems, that were described above, are single-threaded. So, there are many possible ways to jump for multithreading.
One of them by creating actor-based system, that will be fully compatible with Akka.
When Actor receive a Signal message, it's processing will be performed in the most obvious way: signal will be sent next, to embedded DynamicSystem.
There's a special NonSignalWithSenderInput contact. It could be used for compatibility with programs, that doesn't work with Signals.
This contact has (ActorRef, Any) type. The first element will contain received data sender, the second, strictly speaking - data.


1. [Read more about Actor support](Actors.EN.md).

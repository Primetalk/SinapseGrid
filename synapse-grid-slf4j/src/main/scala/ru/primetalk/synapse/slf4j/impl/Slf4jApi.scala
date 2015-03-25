package ru.primetalk.synapse.slf4j.impl

import org.slf4j.LoggerFactory
import ru.primetalk.synapse.core._
import ru.primetalk.synapse.slf4j.LoggingContact
import scala.language.implicitConversions

/**
 * @author zhizhelev, 25.03.15.
 */
trait Slf4jApi {
  class SystemBuilderLoggingExtension(val sb:BasicSystemBuilder) extends SystemBuilderExtension {
    var loggerNamePrefix = sb.systemName.replaceAllLiterally("$", "_") //"ru.primetalk.system2.contacts."
  }
  implicit def contactToLoggingContact[T](c:Contact[T])(implicit sb:BasicSystemBuilder): LoggingContact[T] =
    new LoggingContact(c, sb.extend(SystemBuilderLoggingExtensionId).loggerNamePrefix)(basicSystemBuilderToAdvanced(sb))

  implicit val SystemBuilderLoggingExtensionId = new SystemBuilderExtensionId(new SystemBuilderLoggingExtension(_))

  implicit class LoggingContactThrowable[T<:Throwable](c:Contact[T])(implicit sb:BasicSystemBuilder){

    def loggerName = sb.extend(SystemBuilderLoggingExtensionId).loggerNamePrefix + "." + c.name
    def logger = LoggerFactory.getLogger(loggerName)
    /** Log at level ERROR with message */
    def error(label: T=>String) = {
      this.error((s:Signal[T]) =>label(s.data))
      c
    }
    /** Log at level ERROR with message */
    def error(f: Signal[T] ⇒ String = "" + _) = {
      c.foreach(data ⇒ logger.error(f(Signal(c, data)), data), "ERROR: " + loggerName)
      c
    }
    /** Log at level WARN with message */
    def warn(label: T=>String) = {
      this.error((s:Signal[T]) =>label(s.data))
      c
    }
    /** Log at level WARN with message */
    def warn(f: Signal[T] ⇒ String = "" + _) = {
      c.foreach(data ⇒ logger.warn(f(Signal(c, data)), data), "WARN: " + loggerName)
      c
    }

  }
}

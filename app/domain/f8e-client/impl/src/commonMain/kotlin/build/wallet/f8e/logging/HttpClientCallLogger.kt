package build.wallet.f8e.logging

import build.wallet.logging.logDebug
import build.wallet.logging.logError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * An [HttpClientCallLogger] is attached to every outgoing HTTP request
 * to build log messages over multiple Ktor pipeline phases, only logging
 * the request or response when [closeRequestLog] or [closeResponseLog]
 * are called.
 */
internal class HttpClientCallLogger(private val tag: String?) {
  private val requestLog = StringBuilder()
  private val responseLog = StringBuilder()
  private val requestLoggedMonitor = Job()
  private val responseHeaderMonitor = Job()

  private val requestLogged = MutableStateFlow(false)
  private val responseLogged = MutableStateFlow(false)
  private val closed = MutableStateFlow(false)

  fun logRequest(message: String) {
    requestLog.appendLine(message.trim())
  }

  fun logResponseHeader(message: String) {
    responseLog.appendLine(message.trim())
    responseHeaderMonitor.complete()
  }

  suspend fun logResponseException(
    message: String,
    cause: Throwable,
  ) {
    requestLoggedMonitor.join()
    logError(tag = tag, throwable = cause) { message.trim() }
  }

  suspend fun logResponseBody(message: String) {
    responseHeaderMonitor.join()
    responseLog.append(message)
  }

  fun closeRequestLog() {
    if (!requestLogged.compareAndSet(false, true)) return

    try {
      val message = requestLog.trim().toString()
      if (message.isNotEmpty()) logDebug(tag = tag) { message }
    } finally {
      requestLoggedMonitor.complete()
    }
  }

  suspend fun closeResponseLog() {
    if (!responseLogged.compareAndSet(false, true)) return

    requestLoggedMonitor.join()
    val message = responseLog.trim().toString()
    if (message.isNotEmpty()) logDebug(tag = tag) { message }

    closed.compareAndSet(false, true)
  }

  suspend fun awaitClose() {
    closed.first { it }
  }
}

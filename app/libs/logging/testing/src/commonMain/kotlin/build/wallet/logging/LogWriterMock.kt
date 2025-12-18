package build.wallet.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * A test double for [LogWriter] that captures all log entries for verification.
 */
class LogWriterMock : LogWriter() {
  /**
   * All captured log entries.
   */
  val logs = mutableListOf<LogEntry>()

  /**
   * Asserts that the most recent log entry matches the expected values, then removes it.
   */
  fun expectLog(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable? = null,
  ) {
    val latest = logs.lastOrNull()
    require(latest == LogEntry(severity, message, tag, throwable)) {
      "Expected log entry $severity/$tag: '$message' but got $latest"
    }
    logs.removeAt(logs.lastIndex)
  }

  /**
   * Asserts that no logs have been captured.
   */
  fun expectNoLogs() {
    require(logs.isEmpty()) {
      "Expected no logs but found ${logs.size}: $logs"
    }
  }

  /**
   * Clears all captured logs.
   */
  fun clear() {
    logs.clear()
  }

  data class LogEntry(
    val severity: Severity,
    val message: String,
    val tag: String,
    val throwable: Throwable?,
  )

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    logs.add(
      LogEntry(
        severity = severity,
        message = message,
        tag = tag,
        throwable = throwable
      )
    )
  }
}

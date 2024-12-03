package build.wallet.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LogWriterMock : LogWriter() {
  /**
   * Tracks most recently reported Kermit log entry.
   */
  private var latestLogEntry: LogEntry? = null

  fun expectLog(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable? = null,
  ) {
    latestLogEntry.shouldBe(LogEntry(severity, message, tag, throwable))
    latestLogEntry = null
  }

  fun expectNoLogs() {
    latestLogEntry.shouldBeNull()
  }

  fun clear() {
    latestLogEntry = null
  }

  private data class LogEntry(
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
    latestLogEntry =
      LogEntry(
        severity = severity,
        message = message,
        tag = tag,
        throwable = throwable
      )
  }
}

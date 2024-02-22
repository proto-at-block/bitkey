package build.wallet.logging

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LogWriterMock : KermitLogWriter() {
  /**
   * Tracks most recently reported Kermit log entry.
   */
  private var latestLogEntry: LogEntry? = null

  fun expectLog(
    severity: KermitSeverity,
    message: String,
    tag: String = "build.wallet",
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
    val severity: KermitSeverity,
    val message: String,
    val tag: String,
    val throwable: Throwable?,
  )

  override fun log(
    severity: KermitSeverity,
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

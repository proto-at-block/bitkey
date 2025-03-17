package build.wallet.testing

import build.wallet.logging.LogEntry
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * [LogWriter] that persists logs in memory for later inspection.
 */
internal object TestLogStoreWriter : LogWriter() {
  val logs = mutableListOf<LogEntry>()

  fun clear() {
    logs.clear()
  }

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    logs +=
      LogEntry(
        tag = tag,
        message = message
      )
  }
}

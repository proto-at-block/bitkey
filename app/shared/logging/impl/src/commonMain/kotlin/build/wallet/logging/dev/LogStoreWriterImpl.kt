package build.wallet.logging.dev

import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore.Entity
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Severity.Assert
import co.touchlab.kermit.Severity.Debug
import co.touchlab.kermit.Severity.Error
import co.touchlab.kermit.Severity.Info
import co.touchlab.kermit.Severity.Verbose
import co.touchlab.kermit.Severity.Warn
import kotlinx.datetime.Clock

class LogStoreWriterImpl(
  private val logStore: LogStore,
  private val clock: Clock,
) : LogStoreWriter() {
  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    logStore.record(
      Entity(
        time = clock.now(),
        level = severity.toLogLevel(),
        tag = tag,
        throwable = throwable,
        message = message
      )
    )
  }
}

internal fun Severity.toLogLevel(): LogLevel {
  return when (this) {
    Verbose -> LogLevel.Verbose
    Debug -> LogLevel.Debug
    Info -> LogLevel.Info
    Warn -> LogLevel.Warn
    Error -> LogLevel.Error
    Assert -> LogLevel.Assert
  }
}

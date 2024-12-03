package build.wallet.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger as KermitLogger

object Logger {
  /**
   * Wrapper for configuring Kermit logger.
   *
   * @param minimumLogLevel - sets minimum log level threshold. Logs below this level won't be logged.
   * @param logWriters - log writers to use for the logger. This will override all Kermit's loggers.
   */
  fun configure(
    tag: String,
    minimumLogLevel: LogLevel,
    logWriters: List<LogWriter>,
  ) {
    KermitLogger.apply {
      setTag(tag)
      setMinSeverity(minimumLogLevel.toKermitSeverity())
      setLogWriters(logWriters)
    }
  }
}

/**
 * Logger for [LogLevel.Verbose].
 */
inline fun logVerbose(
  tag: String? = null,
  message: () -> String,
) {
  logInternal(
    level = LogLevel.Verbose,
    tag = tag,
    message = message,
    throwable = null
  )
}

/**
 * Logger for [LogLevel.Debug].
 */
inline fun logDebug(
  tag: String? = null,
  message: () -> String,
) {
  logInternal(
    level = LogLevel.Debug,
    tag = tag,
    message = message,
    throwable = null
  )
}

/**
 * Logger for [LogLevel.Info].
 */
inline fun logInfo(
  tag: String? = null,
  message: () -> String,
) {
  logInternal(
    level = LogLevel.Info,
    tag = tag,
    message = message,
    throwable = null
  )
}

/**
 * Logger for [LogLevel.Warn].
 */
inline fun logWarn(
  tag: String? = null,
  throwable: Throwable? = null,
  message: () -> String,
) {
  logInternal(
    level = LogLevel.Warn,
    tag = tag,
    message = message,
    throwable = throwable
  )
}

/**
 * Logger for [LogLevel.Error].
 */
inline fun logError(
  tag: String? = null,
  throwable: Throwable? = null,
  message: () -> String,
) = logInternal(
  level = LogLevel.Error,
  tag = tag,
  message = message,
  throwable = throwable
)

/**
 * Logger for [LogLevel.Assert].
 */
inline fun logAssert(
  tag: String? = null,
  throwable: Throwable? = null,
  message: () -> String,
) = logInternal(
  level = LogLevel.Assert,
  tag = tag,
  message = message,
  throwable = throwable
)

/**
 * An easy to use logger that can be used for local development purposes.
 * Helps us differentiate app logic logs from local debugging logs.
 * A better alternative to something like `log(tag = "Dev") {}` for lcoal use.
 *
 * Logs messages with default tag [DEV_TAG] and [Error] as default logging level as a way
 * to make logs more prominent.
 *
 * Warning: should not be used in production code. TODO: add a linter.
 */
@Suppress("Unused") // Used for local development and testing purposes.
fun logDev(
  level: LogLevel = LogLevel.Error,
  tag: String? = DEV_TAG,
  throwable: Throwable? = null,
  message: () -> String,
) {
  logInternal(
    level = level,
    tag = tag,
    message = message,
    throwable = throwable
  )
}

/**
 * Convenience wrapper around Kermit logger. In most cases, level specific loggers should be
 * preferred, [logDebug], [logError], etc.
 *
 * The [logInternal] primarily exists to abstract Kermit logger implementation and to implement
 * custom logging extensions, for example [Result.logFailure].
 *
 * @param level [LogLevel] to use, maps to Kermit's [co.touchlab.kermit.Severity] type.
 * @param tag to use for the log entry. If `null`, will default to [KermitLogger.tag].
 * @param throwable cause exception to log, if any.
 */
inline fun logInternal(
  level: LogLevel,
  tag: String? = null,
  throwable: Throwable? = null,
  message: () -> String,
) {
  KermitLogger.logBlock(
    severity = level.toKermitSeverity(),
    tag = tag ?: KermitLogger.tag,
    message = message,
    throwable = throwable
  )
}

package build.wallet.logging

import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Info

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
    logWriters: List<KermitLogWriter>,
  ) {
    KermitLogger.apply {
      setTag(tag)
      setMinSeverity(minimumLogLevel.toKermitSeverity())
      setLogWriters(logWriters)
    }
  }
}

/**
 * Convenience wrapper around Kermit logger.
 *
 * @param level log level to use. If null, will default to [Info] for messages without [Throwable]
 * and [Error] for messages with [Throwable].
 * @param tag to use for the log entry. If null, will default to [KermitLogger.tag].
 * @param throwable cause exception to log, if any. If not null and [level] is null, will use
 * [Error] level.
 */
inline fun log(
  level: LogLevel? = null,
  tag: String? = null,
  throwable: Throwable? = null,
  message: () -> String,
) {
  val levelToUse =
    when (level) {
      null ->
        when (throwable) {
          null -> Info
          // Use error if throwable is present and level is null.
          else -> Error
        }

      else -> level
    }
  KermitLogger.log(
    severity = levelToUse.toKermitSeverity(),
    tag = tag ?: KermitLogger.tag,
    message = message(),
    throwable = throwable
  )
}

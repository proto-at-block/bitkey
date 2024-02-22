package build.wallet.logging.bugsnag

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

expect fun kermitBugsnagLogWriter(
  minSeverity: Severity,
  minCrashSeverity: Severity,
): LogWriter

/**
 * A wrapper around Kermit's [BugsnagLogWriter] (provided by the platforms vi the above
 * expected method) that passes an [HandledError] when there isn't one to make sure all
 * [Error] severity logs get sent to Bugsnag.
 *
 * @property minErrorSeverity: The minimum severity that will cause a handled error to
 * be logged in Bugsnag
 */
internal class BugsnagLogWriter(
  private val minErrorSeverity: Severity,
  private val kermitBugsnagLogWriter: LogWriter,
) : LogWriter() {
  constructor(
    minSeverity: Severity,
    minErrorSeverity: Severity = Severity.Error,
  ) : this(
    minErrorSeverity = minErrorSeverity,
    kermitBugsnagLogWriter =
      kermitBugsnagLogWriter(
        minSeverity = minSeverity,
        minCrashSeverity = minErrorSeverity
      )
  )

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    val nonnullThrowable = throwable ?: HandledError(message)
    kermitBugsnagLogWriter.log(
      severity = severity,
      message = message,
      tag = tag,
      throwable = nonnullThrowable.takeIf { severity >= minErrorSeverity }
    )
  }
}

private class HandledError(
  override val message: String,
) : Throwable(message)

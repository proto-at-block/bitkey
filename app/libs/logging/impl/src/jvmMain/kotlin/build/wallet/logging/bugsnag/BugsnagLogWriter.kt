package build.wallet.logging.bugsnag

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

actual fun kermitBugsnagLogWriter(
  minSeverity: Severity,
  minCrashSeverity: Severity,
): LogWriter =
  object : LogWriter() {
    override fun log(
      severity: Severity,
      message: String,
      tag: String,
      throwable: Throwable?,
    ) {
      // noop
    }
  }

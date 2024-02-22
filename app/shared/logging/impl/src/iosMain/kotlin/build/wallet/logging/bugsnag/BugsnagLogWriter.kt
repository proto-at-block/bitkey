@file:OptIn(ExperimentalKermitApi::class)

package build.wallet.logging.bugsnag

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.bugsnag.BugsnagLogWriter as KermitBugsnagLogWriter

actual fun kermitBugsnagLogWriter(
  minSeverity: Severity,
  minCrashSeverity: Severity,
): LogWriter =
  KermitBugsnagLogWriter(
    minSeverity = minSeverity,
    minCrashSeverity = minCrashSeverity
  )

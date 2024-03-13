package build.wallet.logging

import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.bugsnag.BugsnagLogWriter
import build.wallet.logging.dev.LogStoreWriter
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import co.touchlab.kermit.LogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Initializes Kermit logger:
 * - sets our own logger tag (equivalent to app ID)
 * - registers Bugsnag log writer for error and crash logging
 * - registers [LogStoreWriter] which is used for storing logs in memory to display in debug menu
 * - registers additional customer [logWriters]
 *
 * Additionally sync [LogWriterContext] with latest logging context that gets included on every
 * logs as attributes.
 */
fun initializeLogger(
  appVariant: AppVariant,
  appId: AppId,
  appCoroutineScope: CoroutineScope,
  logWriterContextStore: LogWriterContextStore,
  logStoreWriter: LogStoreWriter,
  logWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
) {
  appCoroutineScope.launch {
    val minimumLogLevel =
      when (appVariant) {
        Development -> Debug
        Team -> Debug
        Beta -> Info
        Customer -> Info
        Emergency -> Info
      }

    logWriterContextStore.syncContext()
    Logger.configure(
      tag = appId.value,
      minimumLogLevel = minimumLogLevel,
      logWriters =
        logWritersProvider(logWriterContextStore)
          .plus(platformLogWriter())
          .plus(logStoreWriter)
          .plus(BugsnagLogWriter(minSeverity = minimumLogLevel.toKermitSeverity()))
    )
  }
}

package build.wallet.logging

import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.bugsnag.BugsnagLogWriter
import build.wallet.logging.dev.LogStoreWriter
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import co.touchlab.kermit.LogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Initializes Kermit logger:
 * - sets our own logger tag (equivalent to app ID)
 * - registers Bugsnag log writer for error and crash logging
 * - registers [LogStoreWriter] which is used for storing logs in memory to display in debug menu
 * - registers additional [LogWriter]s
 *
 * Additionally, syncs [LogWriterContext] with latest logging context that gets included on every
 * log as attributes.
 */
class LoggerInitializer(
  private val logWriterContextStore: LogWriterContextStore,
  private val additionalLogWriters: List<LogWriter>,
  private val appVariant: AppVariant,
  private val appId: AppId,
  private val appCoroutineScope: CoroutineScope,
) {
  private var initialized: Boolean = false
  private val initializeLock = Mutex()

  fun initialize() {
    appCoroutineScope.launch {
      initializeLock.withLock {
        if (initialized) return@launch

        val minimumLogLevel: LogLevel = when (appVariant) {
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
          logWriters = buildList {
            addAll(additionalLogWriters)
            add(platformLogWriter())
            add(BugsnagLogWriter(minSeverity = minimumLogLevel.toKermitSeverity()))
          }
        )
        initialized = true
      }
    }
  }
}
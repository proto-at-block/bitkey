package build.wallet.worker

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementation of [AppWorkerExecutor] that executes all [AppWorker]s provided by
 * an [AppWorkerProvider].
 */

@BitkeyInject(AppScope::class)
class AppWorkerExecutorImpl(
  private val appScope: CoroutineScope,
  private val workerProvider: AppWorkerProvider,
) : AppWorkerExecutor {
  private var executedWorkers = false

  // Mutex lock that ensures thread-safety in case if
  // multiple calls to executeAll() are made concurrently on accident.
  private var executionLock = Mutex()

  override suspend fun executeAll() {
    executionLock.withLock {
      if (executedWorkers) {
        // TODO(W-2000): handle Android configuration changes more gracefully.
        logWarn {
          "Attempted to execute app workers more than once. Ignoring. " +
            "This is likely due to a configuration change on Android, or due to a developer error."
        }
        // safeguard to ensure that app workers are only ever executed once.
        return
      }

      appScope.launch {
        workerProvider.allWorkers().forEach { worker ->
          launch {
            worker.executeWork()
          }
        }
        executedWorkers = true
      }
    }
  }
}

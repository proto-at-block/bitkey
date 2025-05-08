package build.wallet.worker

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logWarn
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Implementation of [AppWorkerExecutor] that executes all [AppWorker]s provided by
 * an [AppWorkerProvider].
 */
@BitkeyInject(AppScope::class)
class AppWorkerExecutorImpl(
  private val appScope: CoroutineScope,
  private val appSessionManager: AppSessionManager,
  private val workerProvider: AppWorkerProvider,
) : AppWorkerExecutor, RefreshExecutor {
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

      workerProvider.allWorkers()
        .flatMap { worker ->
          worker.runStrategy
            .filterIsInstance<RunStrategy.EventStrategy>()
            .map { it to worker }
        }
        .forEach { (strategy, worker) ->
          appScope.launch {
            logDebug { "${worker.label} Waiting for trigger events" }
            strategy.observer.collect {
              runWorker(worker, strategy)
            }
          }
        }
      executedWorkers = true
    }
  }

  override suspend fun runRefreshOperation(refreshOperation: RefreshOperation) {
    logDebug { "Running refresh operation: ${refreshOperation.name}" }
    workerProvider.allWorkers()
      .flatMap { worker ->
        worker.runStrategy.filterIsInstance<RunStrategy.Refresh>()
          .filter {
            when (val type = it.type) {
              is RefreshOperationFilter.Any -> true
              is RefreshOperationFilter.Subset -> refreshOperation in type.operations
            }
          }
          .map { it to worker }
      }
      .map { (strategy, worker) ->
        appScope.async {
          runWorker(worker, strategy)
        }
      }.awaitAll()
  }

  private suspend fun runWorker(
    worker: AppWorker,
    strategy: RunStrategy,
  ) {
    if (!appSessionManager.isAppForegrounded()) {
      when (strategy.backgroundStrategy) {
        is BackgroundStrategy.Allowed -> {
          logDebug { "${worker.label} running in background" }
        }
        is BackgroundStrategy.Wait -> {
          logDebug { "${worker.label} waiting for foreground" }
          appSessionManager.appSessionState
            .first { it == AppSessionState.FOREGROUND }
        }
        is BackgroundStrategy.Skip -> {
          logDebug { "${worker.label} skipping work because application is in background" }
          return
        }
      }
    }

    logDebug { "${worker.label} running" }

    val retries = when (val retryStrategy = worker.retryStrategy) {
      RetryStrategy.Never -> 0
      is RetryStrategy.Always -> retryStrategy.retries
    }
    val retryDelay = when (val retryStrategy = worker.retryStrategy) {
      RetryStrategy.Never -> Duration.ZERO
      is RetryStrategy.Always -> retryStrategy.delay
    }
    val timeout = when (val timeout = worker.timeout) {
      is TimeoutStrategy.Never -> Duration.INFINITE
      is TimeoutStrategy.Always -> timeout.limit
      is TimeoutStrategy.RefreshOnly ->
        timeout.limit
          .takeIf { strategy is RunStrategy.Refresh }
          ?: Duration.INFINITE
    }

    repeat(retries + 1) { attempt ->
      runCatching {
        val time = measureTime {
          withTimeout(timeout) {
            worker.executeWork()
          }
        }
        logDebug { "${worker.label} completed in $time" }
        return
      }.onFailure { error ->
        if (error is CancellationException && error !is TimeoutCancellationException) {
          logDebug { "${worker.label} coroutine is being canceled" }
          throw error
        }

        logError(throwable = error) { "${worker.label} failed" }

        if (attempt == retries) {
          logError { "${worker.label} Reached retry limit of $retries" }
          return
        }

        logDebug { "${worker.label} Retrying after $retryDelay" }
        delay(retryDelay)
      }
    }
  }

  private val AppWorker.label get() = this::class.simpleName?.let {
    "[AppWorker: $it]"
  } ?: "[AppWorker: ${this::class}]"
}

package build.wallet.queueprocessor

import build.wallet.coroutines.flow.launchTicker
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration

/**
 * Utility wrapper to make it easy to pass around an object instead of lambdas
 */
internal class PeriodicQueueProcessorImpl<T>(
  private val queue: Queue<T>,
  private val processor: Processor<T>,
  private val frequency: Duration,
  private val batchSize: Int,
  private val appSessionManager: AppSessionManager,
) : PeriodicProcessor {
  override suspend fun start() {
    coroutineScope {
      launchTicker(frequency) {
        if (appSessionManager.isAppForegrounded()) {
          processQueueInBatches(queue, processor, batchSize)
            .logFailureOrNetworkingFailure { "Failed to process items in queue" }
        }
      }
    }
  }
}

private fun Result<Unit, Error>.logFailureOrNetworkingFailure(message: () -> String) {
  when (val error = getError()) {
    is NetworkingError ->
      mapError { error }
        .logNetworkFailure(message = message)

    else ->
      logFailure(message = message)
  }
}

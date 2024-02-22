package build.wallet.queueprocessor

import build.wallet.coroutines.callCoroutineEvery
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapError
import kotlin.time.Duration

/**
 * Utility wrapper to make it easy to pass around an object instead of lambdas
 */
class PeriodicQueueProcessorImpl<T>(
  private val queue: Queue<T>,
  private val processor: Processor<T>,
  private val frequency: Duration,
  private val batchSize: Int,
) : PeriodicProcessor {
  override suspend fun start() {
    callCoroutineEvery(frequency) {
      processQueueInBatches(queue, processor, batchSize)
        .logFailureOrNetworkingFailure { "Failed to process items in queue" }
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

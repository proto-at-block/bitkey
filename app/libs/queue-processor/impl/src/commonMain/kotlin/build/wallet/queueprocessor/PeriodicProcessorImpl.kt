package build.wallet.queueprocessor

import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import kotlin.time.Duration

/**
 * Class encapsulating the behavior of a processor and retry-on-failure behavior. The batch
 * will be processed immediately and if it fails will be put on a queue for retry at a later point.
 */
class PeriodicProcessorImpl<T>(
  private val queue: Queue<T>,
  private val processor: Processor<T>,
  retryFrequency: Duration,
  retryBatchSize: Int,
  appSessionManager: AppSessionManager,
) : Processor<T>, PeriodicProcessor {
  private val periodicQueueProcessor =
    PeriodicQueueProcessorImpl(queue, processor, retryFrequency, retryBatchSize, appSessionManager)

  override suspend fun processBatch(batch: List<T>): Result<Unit, Error> {
    return coroutineBinding {
      processor.processBatch(batch)
        .onFailure {
          batch.forEach { item ->
            queue.append(item).bind()
          }
        }
    }
  }

  override suspend fun start() {
    this.periodicQueueProcessor.start()
  }
}

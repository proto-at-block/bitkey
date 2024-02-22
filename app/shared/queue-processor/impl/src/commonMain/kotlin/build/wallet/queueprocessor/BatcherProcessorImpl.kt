package build.wallet.queueprocessor

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import kotlin.time.Duration

/**
 * Class encapsulates the batching of multiple processing calls across a time period. Will retry
 * on failure.
 */
class BatcherProcessorImpl<T>(
  private val queue: Queue<T>,
  processor: Processor<T>,
  frequency: Duration,
  batchSize: Int,
) : Processor<T>, PeriodicProcessor {
  private val periodicQueueProcessor =
    PeriodicQueueProcessorImpl(queue, processor, frequency, batchSize)

  override suspend fun processBatch(batch: List<T>): Result<Unit, Error> {
    return binding {
      batch.forEach { item ->
        queue.append(item).bind()
      }
    }
  }

  override suspend fun start() {
    this.periodicQueueProcessor.start()
  }
}

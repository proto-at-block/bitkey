package build.wallet.queueprocessor

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

/**
 * Utility function for batching [batchSize] items from [queue] and processing via [processor].
 * If processing is successful, all items in batch are removed from [queue]. Otherwise, all items
 * are placed on the back of the [queue] for future processing.
 */
suspend fun <T> processQueueInBatches(
  queue: Queue<T>,
  processor: Processor<T>,
  batchSize: Int,
): Result<Unit, Error> {
  return binding {
    var items = queue.take(batchSize).bind()

    while (items.isNotEmpty()) {
      when (val processBatchResult = processor.processBatch(items)) {
        is Err -> {
          queue.moveToEnd(batchSize).bind()
          processBatchResult.bind<Error>()
        }
        is Ok -> {
          queue.removeFirst(batchSize).bind()
          items = queue.take(batchSize).bind()
        }
      }
    }
  }
}

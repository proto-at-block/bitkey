package build.wallet.queueprocessor

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * Utility function for batching [batchSize] items from [queue] and processing via [processor].
 * If processing is successful, all items in batch are removed from [queue]. Otherwise, all items
 * are placed on the back of the [queue] for future processing.
 */
internal suspend fun <T> processQueueInBatches(
  queue: Queue<T>,
  processor: Processor<T>,
  batchSize: Int,
): Result<Unit, Error> {
  return coroutineBinding<Unit, Error> {
    var items = queue.take(batchSize).bind()

    while (items.isNotEmpty()) {
      val processBatchResult = processor.processBatch(items)
      if (processBatchResult.isOk) {
        queue.removeFirst(batchSize).bind()
        items = queue.take(batchSize).bind()
      } else {
        queue.moveToEnd(batchSize).bind()
        processBatchResult.bind()
      }
    }
  }
}

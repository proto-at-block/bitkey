package build.wallet.queueprocessor

import com.github.michaelbull.result.Result

/**
 * Minimal interface for processing [T]
 */
interface Processor<T> {
  /**
   * Process [batch]. Returns true if the entire batch was processed, false otherwise.
   */
  suspend fun processBatch(batch: List<T>): Result<Unit, Error>

  /**
   * Syntactic sugar for a single item process
   */
  suspend fun process(item: T): Result<Unit, Error> = processBatch(listOf(item))
}

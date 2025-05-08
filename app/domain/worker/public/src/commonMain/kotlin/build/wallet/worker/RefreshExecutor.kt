package build.wallet.worker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Runs workers associated with a refresh operation
 */
interface RefreshExecutor {
  /**
   * Execute workers associated with the specified refresh operation.
   */
  suspend fun runRefreshOperation(refreshOperation: RefreshOperation)
}

/**
 * Run multiple refresh operations at the same time in parallel.
 */
suspend fun RefreshExecutor.runRefreshOperations(vararg refreshOperations: RefreshOperation) {
  coroutineScope {
    refreshOperations.map { operation ->
      async {
        runRefreshOperation(operation)
      }
    }.awaitAll()
  }
}

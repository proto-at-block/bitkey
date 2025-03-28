package build.wallet.worker

/**
 * Asynchronously executes all [AppWorker]s provided to an implementation of this interface.
 * The workers are executed on [CoroutineScopes.AppScope] asynchronously.
 *
 * This should be called only once on application startup. Calling this method multiple times
 * has no additional effect, but it is considered a developer error and will be logged as a warning.
 */
interface AppWorkerExecutor {
  /**
   * Executes all provided [AppWorker]s asynchronously, one by one on the [CoroutineScopes.AppScope].
   *
   * The order in which the workers are executed is not guaranteed.
   */
  suspend fun executeAll()
}

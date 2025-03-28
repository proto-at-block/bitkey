package build.wallet.worker

/**
 * Represents a worker for executing some code scoped to the application lifecycle.
 * The worker is executed asynchronously when the application starts by [AppWorkerExecutor].
 */
fun interface AppWorker {
  /**
   * Some code that needs to be executed when application starts.
   * This will only execute once, and will be executed asynchronously.
   *
   * An implementation of the [AppWorkerExecutor] is supposed to inject appropriate
   * [AppWorker]s and execute them.
   *
   * This method **should not** be called directly anywhere in the application otherwise.
   *
   * The order in which the workers are executed is not guaranteed.
   *
   * This coroutine will be only cancelled when the application is terminated.
   */
  suspend fun executeWork()
}

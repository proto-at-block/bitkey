package build.wallet.worker

/**
 * Represents a worker for executing some code scoped to the application lifecycle.
 * The worker is executed asynchronously when the application starts by [AppWorkerExecutor].
 */
fun interface AppWorker {
  /**
   * When to run the worker operation.
   *
   * This set can include multiple strategies, such as at startup in addition
   * to a timed schedule. By default, workers run at startup.
   */
  val runStrategy: Set<RunStrategy> get() = setOf(RunStrategy.Startup())

  /**
   * An optional timeout that can be used to restrict the amount of time
   * the app worker will let the job run for before canceling and logging
   * the run as an error.
   */
  val timeout: TimeoutStrategy get() = TimeoutStrategy.Never

  /**
   * Defines when and how to retry running the worker if it fails.
   */
  val retryStrategy: RetryStrategy get() = RetryStrategy.Never

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

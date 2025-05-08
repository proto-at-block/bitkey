package build.wallet.worker

/**
 * Defines a strategy for running a worker when the app is in the background
 */
sealed interface BackgroundStrategy {
  /**
   * Allow the worker to run when the application is in the background.
   */
  data object Allowed : BackgroundStrategy

  /**
   * Pause the worker until the application returns to the foreground.
   */
  data object Wait : BackgroundStrategy

  /**
   * Abort the worker run entirely if the application is in the background.
   */
  data object Skip : BackgroundStrategy
}

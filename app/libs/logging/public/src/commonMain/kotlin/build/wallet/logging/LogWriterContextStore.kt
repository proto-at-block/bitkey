package build.wallet.logging

/**
 * Updates and provides [LogWriterContext] containing latest attributes.
 */
interface LogWriterContextStore {
  /**
   * Gets latest context.
   * */
  fun get(): LogWriterContext

  /**
   * Launches a non-blocking coroutine that will sync [LogWriterContext] as underlying data sources
   * for the context are updated.
   */
  suspend fun syncContext()
}

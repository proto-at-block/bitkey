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
   * Performs an initial sync of [LogWriterContext] from underlying data sources and keeps the
   * context up to date as those sources change (e.g. hardware serial number updated during
   * pairing or cloud backup restoration).
   */
  suspend fun syncContext()
}

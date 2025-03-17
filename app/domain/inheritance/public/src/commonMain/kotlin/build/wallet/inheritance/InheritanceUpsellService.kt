package build.wallet.inheritance

/**
 * Tracker for whether the InheritanceUpsell screen has been seen or not.
 */
interface InheritanceUpsellService {
  /**
   * Marks the upsell screen as seen.
   */
  suspend fun markUpsellAsSeen()

  /**
   * Returns whether upsell should be shown based on timing and other conditions.
   */
  suspend fun shouldShowUpsell(): Boolean

  /**
   * Resets the upsell screen state.
   */
  suspend fun reset()
}

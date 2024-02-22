package build.wallet.feature

/**
 * A common place for flag initialization that should be used at app launch to
 * initialize all flags from the persistent store.
 */
interface FeatureFlagInitializer {
  /**
   * Initializes all flags managed by this component from the persistent store
   * (calls [initializeFromDao] on each)
   */
  suspend fun initializeAllFlags()
}

package build.wallet.feature

import kotlinx.coroutines.CoroutineScope

interface FeatureFlagSyncer {
  /**
   * Initialize the feature flag sync event loop.
   *
   * To be called during app launch.
   */
  suspend fun initializeSyncLoop(scope: CoroutineScope)

  /**
   * Syncs the local feature flags' values with updated remote values from f8e.
   *
   * It is expected that this sync function will be invoked:
   *  1. on every cold launch of the app
   *  2. on every foregrounding of the app
   *
   * See
   * https://docs.google.com/document/d/1NEzikJQrQDIdvkwSSemAFJYeB3P7CJ33nQL577N5iHY for detailed
   * syncing behavior specifications.
   */
  suspend fun sync()
}

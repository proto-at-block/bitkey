package build.wallet.feature

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service for working with feature flags.
 *
 * Feature flags are initialized on app launch by setting latest or default value from database
 * into memory cache, and are synced periodically with remote values using [FeatureFlagSyncWorker].
 */
interface FeatureFlagService {
  /**
   * A flow that emits `true` when the feature flags have been initialized into cache from database.
   */
  val featureFlagsInitialized: StateFlow<Boolean>

  /**
   * Returns a list of all available feature flags.
   */
  fun getFeatureFlags(): List<FeatureFlag<out FeatureFlagValue>>

  /**
   * Reset all feature flags to their default values and sync with remote.
   */
  suspend fun resetFlags(): Result<Unit, Error>
}

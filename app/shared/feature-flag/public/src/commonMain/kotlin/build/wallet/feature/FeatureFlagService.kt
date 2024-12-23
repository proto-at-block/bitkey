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
  val flagsInitialized: StateFlow<Boolean>

  /**
   * Returns a list of all available feature flags.
   */
  fun getFeatureFlags(): List<FeatureFlag<out FeatureFlagValue>>

  /**
   * Directly initialize the Compose UI feature flag if available for the build type.
   *
   * This is a temporary development flag, to be removed when Compose UI is enabled by default.
   */
  fun initComposeUiFeatureFlag()

  /**
   * Reset all feature flags to their default values and sync with remote.
   */
  suspend fun resetFlags(): Result<Unit, Error>
}

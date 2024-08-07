package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FeatureFlagServiceImpl(
  private val featureFlags: List<FeatureFlag<out FeatureFlagValue>>,
  private val featureFlagSyncer: FeatureFlagSyncer,
) : FeatureFlagService, FeatureFlagSyncWorker {
  private val featureFlagsInitializedState = MutableStateFlow(false)
  override val featureFlagsInitialized: StateFlow<Boolean> = featureFlagsInitializedState

  override fun getFeatureFlags() = featureFlags

  override suspend fun resetFlags(): Result<Unit, Error> {
    for (flag in featureFlags) {
      // Reset feature flag values to default
      flag.reset()
    }

    // Manually sync feature flags with remote after reset
    featureFlagSyncer.sync()

    return Ok(Unit)
  }

  override suspend fun executeWork() {
    coroutineScope {
      // Initialize feature flag cache from database
      featureFlags.forEach {
        it.initializeFromDao()
      }

      featureFlagsInitializedState.value = true

      // Kick off feature flag sync loop to keep flags up to date with remote values
      featureFlagSyncer.initializeSyncLoop(scope = this)

      // Also perform an initial sync
      featureFlagSyncer.sync()
    }
  }
}

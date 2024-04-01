package build.wallet.feature

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class FeatureFlagSyncerMock(
  turbine: (String) -> Turbine<Any>,
) : FeatureFlagSyncer {
  val syncFeatureFlagsCalls = turbine("sync feature flags calls")

  override suspend fun sync() {
    syncFeatureFlagsCalls += Unit
  }
}

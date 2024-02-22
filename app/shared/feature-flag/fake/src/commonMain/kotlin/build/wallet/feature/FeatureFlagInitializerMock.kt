package build.wallet.feature

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class FeatureFlagInitializerMock(
  turbine: (String) -> Turbine<Any>,
) : FeatureFlagInitializer {
  val initializeFeatureFlagsCalls = turbine("initialize feature flags calls")

  override suspend fun initializeAllFlags() {
    initializeFeatureFlagsCalls += Unit
  }
}

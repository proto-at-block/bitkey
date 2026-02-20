package build.wallet.feature.flags

import build.wallet.feature.FeatureFlagValue.BooleanFlag

suspend fun Bdk2FeatureFlag.setBdk2Enabled(enabled: Boolean) {
  setFlagValue(BooleanFlag(enabled))
  initializeFromDao()
}

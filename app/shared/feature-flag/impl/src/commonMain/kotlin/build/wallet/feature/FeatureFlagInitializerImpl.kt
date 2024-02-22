package build.wallet.feature

class FeatureFlagInitializerImpl<T : FeatureFlagValue>(
  private val allFlags: List<FeatureFlag<T>>,
) : FeatureFlagInitializer {
  override suspend fun initializeAllFlags() {
    allFlags.forEach {
      it.initializeFromDao()
    }
  }
}

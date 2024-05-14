package build.wallet.feature

class FeatureFlagInitializerImpl(
  private val allFlags: List<FeatureFlag<*>>,
) : FeatureFlagInitializer {
  override suspend fun initializeAllFlags() {
    allFlags.forEach {
      it.initializeFromDao()
    }
  }
}

package build.wallet.feature

/**
 * Represents potential values that a feature flag can have
 */
sealed interface FeatureFlagValue {
  /** A flag that has a boolean value */
  data class BooleanFlag(val value: Boolean) : FeatureFlagValue
}

suspend fun FeatureFlag<FeatureFlagValue.BooleanFlag>.setFlagValue(value: Boolean) {
  setFlagValue(FeatureFlagValue.BooleanFlag(value))
}

fun FeatureFlag<FeatureFlagValue.BooleanFlag>.isEnabled(): Boolean {
  return flagValue().value.value
}

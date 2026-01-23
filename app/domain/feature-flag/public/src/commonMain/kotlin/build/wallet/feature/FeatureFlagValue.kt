package build.wallet.feature

/**
 * Represents potential values that a feature flag can have
 */
sealed interface FeatureFlagValue {
  /** A flag that has a boolean value */
  data class BooleanFlag(val value: Boolean) : FeatureFlagValue

  data class DoubleFlag(val value: Double) : FeatureFlagValue

  data class StringFlag(val value: String) : FeatureFlagValue
}

suspend fun FeatureFlag<FeatureFlagValue.BooleanFlag>.setFlagValue(value: Boolean) {
  setFlagValue(FeatureFlagValue.BooleanFlag(value))
}

fun FeatureFlagValue.BooleanFlag.isEnabled(): Boolean = value

fun FeatureFlag<FeatureFlagValue.BooleanFlag>.isEnabled(): Boolean {
  return flagValue().value.isEnabled()
}

/**
 * Returns the current integer value of a DoubleFlag feature flag.
 * Values are coerced to be at least 0.
 */
fun FeatureFlag<FeatureFlagValue.DoubleFlag>.intValue(): Int {
  return flagValue().value.value.toInt().coerceAtLeast(0)
}

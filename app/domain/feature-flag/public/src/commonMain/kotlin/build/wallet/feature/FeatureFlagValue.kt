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
 * Returns the current double value of this feature flag.
 */
fun FeatureFlag<FeatureFlagValue.DoubleFlag>.doubleValue(): Double {
  return flagValue().value.value
}

/**
 * Returns the current integer value of a DoubleFlag feature flag.
 * Values are coerced to be at least 0.
 *
 * Note: If using -1 as a "disabled" sentinel, use [doubleValue] instead.
 */
fun FeatureFlag<FeatureFlagValue.DoubleFlag>.intValue(): Int {
  return flagValue().value.value.toInt().coerceAtLeast(0)
}

/**
 * Returns the current string value of this feature flag.
 */
fun FeatureFlag<FeatureFlagValue.StringFlag>.stringValue(): String {
  return flagValue().value.value
}

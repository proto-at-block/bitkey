package build.wallet.money

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether to show features in the app related to multiple fiat currency
 * support. Guards showing currency selection in onboarding and the currency row in Settings.
 */
class MultipleFiatCurrencyEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "multiple-fiat-currency",
    title = "Multiple Fiat Currency Enabled",
    description = "Controls whether or not to show fiat currency selection in onboarding and settings",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

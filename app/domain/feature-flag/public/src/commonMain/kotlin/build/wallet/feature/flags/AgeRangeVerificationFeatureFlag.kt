package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Alpha
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team

/**
 * Feature flag controlling age range verification enforcement.
 *
 * When enabled, the app will check the user's declared age via platform APIs
 * (Google Play Age Signals / Apple DeclaredAgeRange) and block access for minors.
 *
 * This is required for compliance with App Store Accountability Acts (ASAA):
 * - Texas SB2420
 * - Utah age verification requirements
 * - Louisiana age verification requirements
 *
 * Defaults to true for Development, Alpha, and Team variants to enable testing,
 * false for Customer and Emergency variants.
 */
class AgeRangeVerificationFeatureFlag(
  featureFlagDao: FeatureFlagDao,
  appVariant: AppVariant,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "age-range-verification-enabled",
    title = "Age Range Verification",
    description = "Enables age verification and blocks access for users under 18 in applicable jurisdictions (ASAA compliance)",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(
      value = when (appVariant) {
        Development, Alpha, Team -> true
        Customer, Emergency -> false
      }
    ),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

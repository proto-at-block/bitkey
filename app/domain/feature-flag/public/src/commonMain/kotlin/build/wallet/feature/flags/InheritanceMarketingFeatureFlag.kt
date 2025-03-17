package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Controls marketing banners for inheritance separately from the feature
 * enabled flag ([InheritanceFeatureFlag])
 *
 * This allows the inheritance feature to remain enabled separately from
 * marketing efforts pushing users to sign up.
 */
class InheritanceMarketingFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "inheritance-marketing",
    title = "Mobile Inheritance Upsell Marketing",
    description = "Controls whether mobile inheritance marketing upsell screens are shown",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

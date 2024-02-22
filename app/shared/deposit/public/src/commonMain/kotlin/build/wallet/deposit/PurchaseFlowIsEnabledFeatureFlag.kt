package build.wallet.deposit

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether or not "Purchase" button is shown when entering the Partnership's Add Flow
 */
class PurchaseFlowIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
  value: Boolean = false,
) : FeatureFlag<BooleanFlag>(
    identifier = "purchase-flow-is-enabled",
    title = "Purchase Flow Enabled",
    description = "Controls whether or not we can view the 'Purchase' button when entering the Partnership’s Add Flow",
    defaultFlagValue = BooleanFlag(value),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )

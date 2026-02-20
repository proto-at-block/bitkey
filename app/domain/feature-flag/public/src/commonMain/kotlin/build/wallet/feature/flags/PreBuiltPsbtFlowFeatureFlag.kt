package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag for enabling pre-built PSBT flow for sending transactions.
 *
 * When enabled, allows using a pre-built Partially Signed Bitcoin Transaction (PSBT)
 * for sending transactions instead of building it at the end of the send flow
 */
class PreBuiltPsbtFlowFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-pre-built-psbt-flow-enabled",
    title = "Pre-built PSBT Flow",
    description = "Enables pre-built PSBT flow for sending transactions.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

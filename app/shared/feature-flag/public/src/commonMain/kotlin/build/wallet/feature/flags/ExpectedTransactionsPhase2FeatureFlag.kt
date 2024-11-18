package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether to show additional details about partnership transactions.
 */
class ExpectedTransactionsPhase2FeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-expected-transactions-phase-2-is-enabled",
    title = "Expected Transactions Phase 2",
    description = "Show additional details about partnership transactions, such as icons and status",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )

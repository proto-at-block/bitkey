package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class UtxoMaxConsolidationCountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-utxo-consolidation-max-count",
    title = "Mobile UTXO Consolidation Max Count",
    description = "Defines the maximum number of utxos that can be consolidated at once",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(-1.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )

package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to enable CloudKit as the primary backup storage on iOS.
 *
 * When OFF: Uses Key-Value Store (KVS) only (existing behavior).
 * When ON: Uses CloudKit as primary storage with KVS fallback and migration enabled.
 *
 * Defaults to false.
 */
class IosCloudKitBackupFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-ios-cloudkit-backup-enabled",
    title = "iOS CloudKit Backup",
    description = "Use CloudKit as primary backup storage with KVS fallback",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

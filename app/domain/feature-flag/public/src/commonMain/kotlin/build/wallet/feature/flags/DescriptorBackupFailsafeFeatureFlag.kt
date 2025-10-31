package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag controlling the descriptor backup failsafe behavior.
 *
 * When enabled, enforces descriptor backup check for private keysets, blocking address generation
 * if backup is missing.
 * When disabled, no checks are performed.
 */
class DescriptorBackupFailsafeFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-descriptor-backup-failsafe",
    title = "Descriptor Backup Failsafe",
    description = "Enforces descriptor backup verification before address generation",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

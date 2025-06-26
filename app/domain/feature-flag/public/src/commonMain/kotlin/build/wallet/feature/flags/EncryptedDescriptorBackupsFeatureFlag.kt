package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class EncryptedDescriptorBackupsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-encrypted-descriptor-backups-is-enabled",
    title = "Enable Encrypted Descriptor Backups",
    description = "The app uses local descriptors and/or encrypted descriptor backups (stored on f8e)" +
      " for operations instead of relying on F8e's unencrypted descriptor.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

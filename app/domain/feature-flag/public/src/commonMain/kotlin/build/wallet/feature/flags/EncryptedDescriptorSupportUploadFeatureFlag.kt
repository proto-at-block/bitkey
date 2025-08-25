package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class EncryptedDescriptorSupportUploadFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-encrypted-descriptor-support-upload-is-enabled",
    title = "Enable Encrypted Descriptor Support Upload",
    description = "There is an option when submitting a support ticket to upload an encrypted descriptor to help with additional debugging.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class InheritanceUseEncryptedDescriptorFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-inheritance-use-encrypted-descriptor",
    title = "Inheritance Use Encrypted Descriptor",
    description = "Benefactor uploads and beneficiary uses encrypted descriptor for sweep txn",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

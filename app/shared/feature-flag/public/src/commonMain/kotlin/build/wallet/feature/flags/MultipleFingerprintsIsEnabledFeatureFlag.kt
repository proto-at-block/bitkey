package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether a user can enter the multiple fingerprints flow.
 *
 * Defaults to false on all builds
 */
class MultipleFingerprintsIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-multiple-fingerprints-is-enabled",
    title = "Multiple Fingerprints Is Enabled",
    description = "Allows you to create and manage multiple fingerprint enrollments.",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )

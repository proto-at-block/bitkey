package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Enables replacing an onboarding Full Account with a Lite Account when a full
 * Account cloud backup with a different account ID is discovered during TC onboarding.
 *
 * Defaults to false on all builds.
 */
class ReplaceFullWithLiteAccountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-replace-full-with-lite-account-is-enabled",
    title = "Replace Full with Lite Account",
    description = "Enable replacing a Full Account with a Lite Account when a full backup is found during TC onboarding",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )

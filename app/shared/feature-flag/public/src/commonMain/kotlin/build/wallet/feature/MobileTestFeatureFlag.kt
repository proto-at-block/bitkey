package build.wallet.feature

/**
 * Test flag for remote feature flag syncing. Configured on the server but only used in the debug menu.
 */
class MobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-test-flag",
    title = "Mobile Test Feature Flag",
    description = "Test flag for remote feature flag syncing",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

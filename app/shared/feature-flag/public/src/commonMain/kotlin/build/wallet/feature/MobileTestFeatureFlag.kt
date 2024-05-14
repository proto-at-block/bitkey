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

class DoubleMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-test-flag-double",
    title = "Double Mobile Test Feature Flag",
    description = "This is a test flag with a Number type",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(0.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )

class StringFlagMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-test-flag-string",
    title = "String Mobile Test Feature Flag",
    description = "This is a test flag with a String type",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )

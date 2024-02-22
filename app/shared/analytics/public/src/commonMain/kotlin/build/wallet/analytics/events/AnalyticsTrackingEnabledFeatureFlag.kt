package build.wallet.analytics.events

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer

/**
 * Flag determining whether or not analytics events tracking is enabled.
 * Defaults to true on production builds and false on internal builds.
 */
class AnalyticsTrackingEnabledFeatureFlag(
  appVariant: AppVariant,
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "analytics-tracking-is-enabled",
    title = "Analytics Tracking Enabled",
    description =
      "Controls whether or not to track analytics events",
    defaultFlagValue = BooleanFlag(appVariant == Customer),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )

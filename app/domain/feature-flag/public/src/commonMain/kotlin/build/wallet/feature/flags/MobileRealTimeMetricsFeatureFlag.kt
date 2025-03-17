package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Enables real-time mobile metrics for critical user journeys
 */
class MobileRealTimeMetricsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-real-time-metrics-enabled",
    title = "Mobile Real Time Metrics",
    description = "Enables real-time mobile metrics for critical user journeys.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

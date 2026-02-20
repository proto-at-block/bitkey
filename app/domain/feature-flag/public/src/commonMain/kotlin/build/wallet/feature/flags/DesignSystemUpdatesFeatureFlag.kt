package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to enable the new design system style tokens.
 * When enabled, the app will use updated typography, fonts, and other design tokens.
 * Default is off to preserve existing behavior.
 */
class DesignSystemUpdatesFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-design-system-updates-enabled",
    title = "Design System Updates",
    description = "Enable new design system style tokens (typography, fonts, etc.)",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

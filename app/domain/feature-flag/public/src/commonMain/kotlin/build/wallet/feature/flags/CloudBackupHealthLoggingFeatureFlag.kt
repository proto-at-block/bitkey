package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to enable detailed logging during cloud backup health checks.
 * When enabled, logs backup mismatch details including size and field differences.
 *
 * Defaults to false.
 */
class CloudBackupHealthLoggingFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-cloud-backup-health-logging-enabled",
    title = "Cloud Backup Health Logging",
    description = "Enable detailed logging during cloud backup health checks",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

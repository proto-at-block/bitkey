package build.wallet.cloud.backup

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Enables Cloud Backup Health feature for Mobile Key and EAK backups (Full Accounts only):
 * - periodic cloud backup integrity checks
 * - Money Home warning cards when there is an issue with cloud backups
 * - Settings dashboard for Cloud Backup Health
 */
class CloudBackupHealthFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "cloud-backup-health-enabled",
    title = "Cloud Backup Health",
    description = "Enables Cloud Backup Health feature to ensure integrity of Mobile Key and EAK cloud backups. Full Accounts only.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

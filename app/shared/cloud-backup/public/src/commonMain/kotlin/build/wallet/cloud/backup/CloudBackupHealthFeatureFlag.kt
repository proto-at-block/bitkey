package build.wallet.cloud.backup

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Development

/**
 * Enables Cloud Backup Health feature for Mobile Key and EAK backups (Full Accounts only):
 * - periodic cloud backup integrity checks
 * - Money Home warning cards when there is an issue with cloud backups
 * - Settings dashboard for Cloud Backup Health
 */
class CloudBackupHealthFeatureFlag(
  featureFlagDao: FeatureFlagDao,
  appVariant: AppVariant,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "cloud-backup-health-enabled",
    title = "Cloud Backup Health",
    description = "Enables Cloud Backup Health feature to ensure integrity of Mobile Key and EAK cloud backups. Full Accounts only.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(value = appVariant == Development),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )

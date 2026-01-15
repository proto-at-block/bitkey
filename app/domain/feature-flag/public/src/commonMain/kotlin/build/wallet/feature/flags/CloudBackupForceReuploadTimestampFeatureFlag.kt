package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag containing a timestamp string (ISO 8601 format).
 * If the existing backup's lastUploaded timestamp is older than this timestamp,
 * the backup will be marked as stale during health checks and automatically re-uploaded.
 *
 * Default is empty string, which disables the forced re-upload feature.
 * Set to an ISO 8601 timestamp string (e.g., "2026-01-14T00:00:00Z") to enable.
 */
class CloudBackupForceReuploadTimestampFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "cloud-backup-force-reupload-timestamp",
    title = "Cloud Backup Force Reupload Timestamp",
    description = "If set to a valid timestamp, forces backup re-upload for backups older than this timestamp. Format: ISO 8601 (e.g., 2026-01-14T00:00:00Z). Empty = disabled.",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )

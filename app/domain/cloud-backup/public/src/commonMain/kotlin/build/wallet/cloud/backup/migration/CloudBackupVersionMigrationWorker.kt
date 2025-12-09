package build.wallet.cloud.backup.migration

import build.wallet.worker.AppWorker

/**
 * Performs one-time migration of cloud backups from older schema versions to the latest version.
 *
 * Currently handles CloudBackupV2 → CloudBackupV3 migration for Lite & Full accounts.
 *
 * This worker runs at app startup and foreground events, checking if the locally stored
 * backup needs to be upgraded. Once a backup is migrated, no further action is taken
 * for that account.
 *
 * The migration:
 * - Preserves all existing backup data
 * - Adds new fields required by the latest schema (e.g., deviceNickname, createdAt)
 * - Uploads the migrated backup to cloud storage
 * - Updates the local backup cache
 */
interface CloudBackupVersionMigrationWorker : AppWorker

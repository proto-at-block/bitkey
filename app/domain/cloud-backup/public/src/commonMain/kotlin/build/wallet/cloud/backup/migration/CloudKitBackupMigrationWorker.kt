package build.wallet.cloud.backup.migration

import build.wallet.worker.AppWorker

/**
 * Background worker that writes existing cloud backups to CloudKit on iOS.
 *
 * When the CloudKit feature flag is enabled, this worker ensures that accounts
 * with local backups have their data written to CloudKit. This handles the
 * transition from iCloud Key-Value Store to CloudKit as the primary storage.
 *
 * No-op on Android and JVM where CloudKit is not available.
 */
interface CloudKitBackupMigrationWorker : AppWorker

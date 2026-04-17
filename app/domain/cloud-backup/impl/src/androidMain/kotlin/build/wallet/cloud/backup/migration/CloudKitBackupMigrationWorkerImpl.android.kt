package build.wallet.cloud.backup.migration

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.worker.RunStrategy

/**
 * No-op implementation for Android.
 *
 * CloudKit is an iOS-only technology; Android uses Google Drive for cloud backups.
 */
@BitkeyInject(AppScope::class)
class CloudKitBackupMigrationWorkerImpl : CloudKitBackupMigrationWorker {
  override val runStrategy: Set<RunStrategy> = emptySet()

  override suspend fun executeWork() = Unit
}

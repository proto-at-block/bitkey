package build.wallet.cloud.backup.migration

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.worker.RunStrategy

/**
 * No-op implementation for JVM/Desktop.
 *
 * CloudKit is an iOS-only technology; desktop targets do not use cloud backups.
 */
@BitkeyInject(AppScope::class)
class CloudKitBackupMigrationWorkerImpl : CloudKitBackupMigrationWorker {
  override val runStrategy: Set<RunStrategy> = emptySet()

  override suspend fun executeWork() = Unit
}

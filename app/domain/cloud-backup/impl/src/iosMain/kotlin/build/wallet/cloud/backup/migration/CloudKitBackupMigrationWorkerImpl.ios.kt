package build.wallet.cloud.backup.migration

import build.wallet.cloud.backup.CloudBackupOperationLock
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.withLock

/**
 * Background worker that writes cloud backups to CloudKit on iOS.
 *
 * Runs at app startup and when CloudKit feature flag updates. Gated by the CloudKit feature flag and skipped
 * entirely in the Emergency (EEK) variant.
 *
 */
@BitkeyInject(AppScope::class)
class CloudKitBackupMigrationWorkerImpl(
  private val appVariant: AppVariant,
  private val iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
  private val cloudBackupOperationLock: CloudBackupOperationLock,
  private val cloudKitBackupMigrationService: CloudKitBackupMigrationService,
  private val cloudKitBackupMigrationStatusDao: CloudKitBackupMigrationStatusDao,
) : CloudKitBackupMigrationWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(backgroundStrategy = BackgroundStrategy.Wait),
    RunStrategy.OnEvent(
      observer = iosCloudKitBackupFeatureFlag.flagValue().drop(1),
      backgroundStrategy = BackgroundStrategy.Wait
    )
  )

  override suspend fun executeWork() {
    if (appVariant == AppVariant.Emergency) return
    cloudBackupOperationLock.withLock {
      if (!iosCloudKitBackupFeatureFlag.isEnabled()) {
        cloudKitBackupMigrationStatusDao.clear()
          .logFailure { "CloudKit backup migration status clear failed" }
        return@withLock
      }

      cloudKitBackupMigrationService.migrateIfNeeded()
    }
  }
}

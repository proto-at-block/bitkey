package build.wallet.cloud.backup

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.health.CloudBackupStatus
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import kotlinx.coroutines.flow.StateFlow

interface CloudBackupHealthRepository {
  /**
   * Emits latest App Key backup status.
   */
  fun mobileKeyBackupStatus(): StateFlow<MobileKeyBackupStatus?>

  /**
   * Emits latest Emergency Exit Kit (EEK) backup status.
   */
  fun eekBackupStatus(): StateFlow<EekBackupStatus?>

  /**
   * Launches a non-blocking coroutine that periodically syncs [mobileKeyBackupStatus] and
   * [eekBackupStatus].
   */
  suspend fun syncLoop(account: FullAccount)

  /**
   * Queues a sync of [mobileKeyBackupStatus] and [eekBackupStatus], and returns immediately.
   * The actual sync is performed asynchronously through [syncLoop].
   */
  fun requestSync(account: FullAccount)

  /**
   * Performs an on-demand sync of [mobileKeyBackupStatus] and [eekBackupStatus], and returns the
   * result. Attempts to resolve certain backup issues on background, for example missing backup.
   * If the issue cannot be resolved silently, the customer will be prompted to resolve it through
   * the app.
   */
  suspend fun performSync(account: FullAccount): CloudBackupStatus
}

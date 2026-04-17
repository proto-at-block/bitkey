package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.CloudBackupStatus
import build.wallet.cloud.backup.health.EekBackupStatus
import kotlinx.coroutines.flow.StateFlow

interface CloudBackupHealthRepository {
  /**
   * Emits latest App Key backup status.
   */
  fun appKeyBackupStatus(): StateFlow<AppKeyBackupStatus?>

  /**
   * Emits latest Emergency Exit Kit (EEK) backup status.
   */
  fun eekBackupStatus(): StateFlow<EekBackupStatus?>

  /**
   * Performs an on-demand sync of [appKeyBackupStatus] and [eekBackupStatus], and returns the
   * result. Attempts to resolve certain backup issues on background, for example missing backup.
   * If the issue cannot be resolved silently, the customer will be prompted to resolve it through
   * the app.
   */
  suspend fun performSync(
    accountId: FullAccountId,
    keybox: Keybox,
  ): CloudBackupStatus
}

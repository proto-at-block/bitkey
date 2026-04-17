package build.wallet.cloud.backup.health

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.store.CloudStoreAccount

interface FullAccountCloudBackupRepairer {
  /**
   * Attempts to silently repair Full Account's cloud backup issues on background, before
   * prompting the user to resolve the issue manually.
   *
   * Currently only supports repairing missing App Key Backup or Emergency Exit Kit.
   */
  suspend fun attemptRepair(
    accountId: FullAccountId,
    keybox: Keybox,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  )
}

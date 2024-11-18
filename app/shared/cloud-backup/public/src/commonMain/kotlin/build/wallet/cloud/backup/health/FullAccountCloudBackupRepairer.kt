package build.wallet.cloud.backup.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccount

interface FullAccountCloudBackupRepairer {
  /**
   * Attempts to silently repair Full Account's cloud backup issues on background, before
   * prompting the user to resolve the issue manually.
   *
   * Currently only supports repairing missing Mobile Key Backup or Emergency Access Kit.
   */
  suspend fun attemptRepair(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  )
}

package build.wallet.cloud.backup

import build.wallet.bitkey.account.LiteAccount
import com.github.michaelbull.result.Result

/** Restores a [LiteAccount] from a [CloudBackupV2]. */
interface LiteAccountCloudBackupRestorer {
  /**
   * Restore a lite account cloud backup.
   *
   * Saves a [LiteAccount], putting it into an 'onboarding' state. Responsibility for activating
   * the account is left to the caller.
   */
  suspend fun restoreFromBackup(
    liteAccountCloudBackup: CloudBackupV2,
  ): Result<LiteAccount, RestoreFromBackupError>
}

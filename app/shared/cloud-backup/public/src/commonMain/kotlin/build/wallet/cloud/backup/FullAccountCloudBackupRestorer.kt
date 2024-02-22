package build.wallet.cloud.backup

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

interface FullAccountCloudBackupRestorer {
  /**
   * Attempts to restore a [Keybox] from its backup. If successful, we store all private keys
   * locally in secure storage, but won't activate the [Keybox] - that's up to the caller.
   *
   * @param cloudBackup the backup to restore from. [CloudBackup.sealedCSEK] will be used to decrypt the
   * backup. Expected that at this point, unsealed [Csek] is persisted in [CsekDao]. If not,
   * returns [CsekMissing] error.
   */
  suspend fun restoreFromBackup(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, RestoreFromBackupError>

  /**
   * Attempts to restore a [Keybox] from its backup. The encrypted part of the backup is already
   * decrypted here. If successful, we store all private keys locally in secure storage, but won't
   * activate the [Keybox] - that's up to the caller.
   *
   * @param cloudBackup the backup to restore from. [CloudBackup.sealedCSEK] will be used to decrypt the
   * backup. Expected that at this point, unsealed [Csek] is persisted in [CsekDao]. If not,
   * returns [CsekMissing] error.
   */
  suspend fun restoreFromBackupWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, RestoreFromBackupError>

  data class AccountRestoration(
    val activeSpendingKeyset: SpendingKeyset,
    val inactiveKeysets: ImmutableList<SpendingKeyset>,
    val activeKeyBundle: AppKeyBundle,
    val config: KeyboxConfig,
    /** To be saved in CloudBackupDao */
    val cloudBackupForLocalStorage: CloudBackup,
  ) {
    fun asKeybox(
      localId: String,
      fullAccountId: FullAccountId,
    ) = Keybox(
      localId = localId,
      fullAccountId = fullAccountId,
      activeSpendingKeyset = activeSpendingKeyset,
      inactiveKeysets = inactiveKeysets,
      activeKeyBundle = activeKeyBundle,
      config = config
    )
  }
}

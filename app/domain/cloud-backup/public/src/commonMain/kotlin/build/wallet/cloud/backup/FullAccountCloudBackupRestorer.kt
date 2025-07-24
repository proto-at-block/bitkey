package build.wallet.cloud.backup

import bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Result

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
    /** All keysets, including the active one and any inactive ones */
    val keysets: List<SpendingKeyset>,
    val activeAppKeyBundle: AppKeyBundle,
    val activeHwKeyBundle: HwKeyBundle,
    val config: FullAccountConfig,
    /** To be saved in CloudBackupDao */
    val cloudBackupForLocalStorage: CloudBackup,
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ) {
    fun asKeybox(
      keyboxId: String,
      fullAccountId: FullAccountId,
    ) = Keybox(
      localId = keyboxId,
      fullAccountId = fullAccountId,
      activeSpendingKeyset = activeSpendingKeyset,
      activeAppKeyBundle = activeAppKeyBundle,
      activeHwKeyBundle = activeHwKeyBundle,
      config = config,
      keysets = keysets.ifEmpty { listOf(activeSpendingKeyset) },
      // Keysets are only persisted if they are valid and authoritative, thus we can use their presence
      // to determine canUseKeyboxKeysets.
      canUseKeyboxKeysets = keysets.isNotEmpty(),
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
    )
  }
}

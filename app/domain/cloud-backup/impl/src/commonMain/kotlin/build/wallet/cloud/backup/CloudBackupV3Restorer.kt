package build.wallet.cloud.backup

import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Result

/** Produce an [AccountRestoration] from a [CloudBackupV3].*/
interface CloudBackupV3Restorer {
  suspend fun restore(
    cloudBackupV3: CloudBackupV3,
  ): Result<AccountRestoration, CloudBackupV3RestorerError>

  suspend fun restoreWithDecryptedKeys(
    cloudBackupV3: CloudBackupV3,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupV3RestorerError>

  /**
   * FOR TESTS ONLY
   *
   * You want [restore] or [restoreWithDecryptedKeys] in production code instead.
   *
   * Decrypts a cloud backup and returns its corresponding [FullAccountKeys]. [restore] calls this
   * as an implementation detail.
   */
  suspend fun decryptCloudBackup(
    cloudBackupV3: CloudBackupV3,
  ): Result<FullAccountKeys, CloudBackupV3RestorerError>

  sealed class CloudBackupV3RestorerError : Error() {
    data object PkekMissingError : CloudBackupV3RestorerError()

    data class AccountBackupDecodingError(
      override val cause: Throwable,
    ) : CloudBackupV3RestorerError()

    data class AppAuthKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupV3RestorerError()

    data class AppSpendingKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupV3RestorerError()

    data class SocRecTrustedContactIdentityKeyStorageError(
      override val cause: Throwable,
    ) : CloudBackupV3RestorerError()

    data class AccountBackupDecryptionError(
      override val cause: Throwable,
    ) : CloudBackupV3RestorerError()
  }
}

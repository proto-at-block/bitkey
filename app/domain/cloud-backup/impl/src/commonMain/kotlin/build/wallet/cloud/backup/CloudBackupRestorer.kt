package build.wallet.cloud.backup

import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Result

/**
 * Produces an [AccountRestoration] from a [CloudBackup].
 *
 * Supports all cloud backup versions (V2, V3, and future versions).
 */
interface CloudBackupRestorer {
  suspend fun restore(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, CloudBackupRestorerError>

  suspend fun restoreWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupRestorerError>

  /**
   * FOR TESTS ONLY
   *
   * You want [restore] or [restoreWithDecryptedKeys] in production code instead.
   *
   * Decrypts a cloud backup and returns its corresponding [FullAccountKeys]. [restore] calls this
   * as an implementation detail.
   */
  suspend fun decryptCloudBackup(
    cloudBackup: CloudBackup,
  ): Result<FullAccountKeys, CloudBackupRestorerError>

  sealed class CloudBackupRestorerError : Error() {
    data object PkekMissingError : CloudBackupRestorerError()

    data class AccountBackupDecodingError(
      override val cause: Throwable,
    ) : CloudBackupRestorerError()

    data class AppAuthKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupRestorerError()

    data class AppSpendingKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupRestorerError()

    data class SocRecTrustedContactIdentityKeyStorageError(
      override val cause: Throwable,
    ) : CloudBackupRestorerError()

    data class AccountBackupDecryptionError(
      override val cause: Throwable,
    ) : CloudBackupRestorerError()
  }
}

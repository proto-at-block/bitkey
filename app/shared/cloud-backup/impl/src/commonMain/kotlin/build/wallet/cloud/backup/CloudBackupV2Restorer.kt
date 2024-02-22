package build.wallet.cloud.backup

import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Result

/** Produce an [AccountRestoration] from a [CloudBackupV2].*/
interface CloudBackupV2Restorer {
  suspend fun restore(
    cloudBackupV2: CloudBackupV2,
  ): Result<AccountRestoration, CloudBackupV2RestorerError>

  suspend fun restoreWithDecryptedKeys(
    cloudBackupV2: CloudBackupV2,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupV2RestorerError>

  sealed class CloudBackupV2RestorerError : Error() {
    data object PkekMissingError : CloudBackupV2RestorerError()

    data class AccountBackupDecodingError(
      override val cause: Throwable,
    ) : CloudBackupV2RestorerError()

    data class AppAuthKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupV2RestorerError()

    data class AppSpendingKeypairStorageError(
      override val cause: Throwable,
    ) : CloudBackupV2RestorerError()

    data class SocRecTrustedContactIdentityKeyStorageError(
      override val cause: Throwable,
    ) : CloudBackupV2RestorerError()
  }
}

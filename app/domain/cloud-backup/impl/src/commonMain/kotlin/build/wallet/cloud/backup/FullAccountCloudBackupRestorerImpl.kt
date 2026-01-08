package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupRestorer.CloudBackupRestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.RestoreFromBackupError.*
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class FullAccountCloudBackupRestorerImpl(
  private val cloudBackupRestorer: CloudBackupRestorer,
) : FullAccountCloudBackupRestorer {
  override suspend fun restoreFromBackup(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, RestoreFromBackupError> {
    logDebug { "Attempting to restore keybox from ${cloudBackup::class.simpleName}" }
    return cloudBackupRestorer.restore(cloudBackup)
      .mapError(::mapRestoreErrors)
  }

  override suspend fun restoreFromBackupWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, RestoreFromBackupError> =
    cloudBackupRestorer.restoreWithDecryptedKeys(cloudBackup, keysInfo)
      .mapError(::mapRestoreErrors)

  private fun mapRestoreErrors(error: CloudBackupRestorerError): RestoreFromBackupError =
    when (error) {
      CloudBackupRestorerError.PkekMissingError -> {
        CsekMissing
      }

      is CloudBackupRestorerError.AccountBackupDecodingError -> {
        AccountBackupDecodingError(
          cause = error.cause,
          message = "Error decoding Cloud Backup"
        )
      }

      is CloudBackupRestorerError.AppAuthKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppAuthKeypair"
        )
      }

      is CloudBackupRestorerError.AppSpendingKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppSpendingKeypair"
        )
      }

      is CloudBackupRestorerError.SocRecTrustedContactIdentityKeyStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing Social Recovery DelegatedDecryptionKey"
        )
      }

      is CloudBackupRestorerError.AccountBackupDecryptionError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error decrypting Cloud Backup"
        )
      }
    }
}

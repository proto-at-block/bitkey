package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError
import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.RestoreFromBackupError.*
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class FullAccountCloudBackupRestorerImpl(
  private val cloudBackupV2Restorer: CloudBackupV2Restorer,
  private val cloudBackupV3Restorer: CloudBackupV3Restorer,
) : FullAccountCloudBackupRestorer {
  override suspend fun restoreFromBackup(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, RestoreFromBackupError> {
    return when (cloudBackup) {
      is CloudBackupV3 -> {
        logDebug { "Attempting to restore keybox from v3 backup" }
        cloudBackupV3Restorer.restore(cloudBackup)
          .mapError(::mapBackupV3RestoreErrors)
      }
      is CloudBackupV2 -> {
        logDebug { "Attempting to restore keybox from v2 backup" }
        cloudBackupV2Restorer.restore(cloudBackup)
          .mapError(::mapBackupV2RestoreErrors)
      }
    }
  }

  private fun mapBackupV3RestoreErrors(error: CloudBackupV3RestorerError) =
    when (error) {
      CloudBackupV3RestorerError.PkekMissingError -> {
        CsekMissing
      }

      is CloudBackupV3RestorerError.AccountBackupDecodingError -> {
        AccountBackupDecodingError(
          cause = error.cause,
          message = "Error decoding Cloud Backup"
        )
      }

      is CloudBackupV3RestorerError.AppAuthKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppAuthKeypair"
        )
      }

      is CloudBackupV3RestorerError.AppSpendingKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppSpendingKeypair"
        )
      }

      is CloudBackupV3RestorerError.SocRecTrustedContactIdentityKeyStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing Social Recovery DelegatedDecryptionKey"
        )
      }
      is CloudBackupV3RestorerError.AccountBackupDecryptionError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error decrypting Cloud Backup"
        )
      }
    }

  private fun mapBackupV2RestoreErrors(error: CloudBackupV2RestorerError) =
    when (error) {
      CloudBackupV2RestorerError.PkekMissingError -> {
        CsekMissing
      }

      is CloudBackupV2RestorerError.AccountBackupDecodingError -> {
        AccountBackupDecodingError(
          cause = error.cause,
          message = "Error decoding Cloud Backup"
        )
      }

      is CloudBackupV2RestorerError.AppAuthKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppAuthKeypair"
        )
      }

      is CloudBackupV2RestorerError.AppSpendingKeypairStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing AppSpendingKeypair"
        )
      }

      is CloudBackupV2RestorerError.SocRecTrustedContactIdentityKeyStorageError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error storing Social Recovery DegegatedDecryptionKey"
        )
      }
      is CloudBackupV2RestorerError.AccountBackupDecryptionError -> {
        AccountBackupRestorationError(
          cause = error.cause,
          message = "Error decrypting Cloud Backup"
        )
      }
    }

  override suspend fun restoreFromBackupWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, RestoreFromBackupError> =
    when (cloudBackup) {
      is CloudBackupV2 ->
        cloudBackupV2Restorer.restoreWithDecryptedKeys(cloudBackup, keysInfo)
          .mapError(::mapBackupV2RestoreErrors)
      is CloudBackupV3 ->
        cloudBackupV3Restorer.restoreWithDecryptedKeys(cloudBackup, keysInfo)
          .mapError(::mapBackupV3RestoreErrors)
      else ->
        Err(
          AccountBackupRestorationError(
            null,
            "Unsupported backup type ${cloudBackup::class.simpleName}"
          )
        )
    }
}

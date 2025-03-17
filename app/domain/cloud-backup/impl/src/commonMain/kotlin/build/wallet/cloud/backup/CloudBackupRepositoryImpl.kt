package build.wallet.cloud.backup

import bitkey.auth.AuthTokenScope
import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.serialization.json.Json

@BitkeyInject(AppScope::class)
class CloudBackupRepositoryImpl(
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudBackupDao: CloudBackupDao,
  private val authTokensService: AuthTokensService,
) : CloudBackupRepository {
  // Key used to store backups in cloud key-value store
  private val cloudBackupKey = "cloud-backup"

  override suspend fun readBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> =
    coroutineBinding {
      // Read encoded backup in JSON format, if any
      val backupEncoded: String? =
        cloudKeyValueStore
          .getString(cloudStoreAccount, cloudBackupKey)
          .mapPossibleRectifiableErrors()
          .bind()

      when (backupEncoded) {
        null -> null
        else ->
          // Found encoded app data
          // Attempt to decode as V2 backup
          Json.decodeFromStringResult<CloudBackupV2>(backupEncoded)
            .mapError {
              UnrectifiableCloudBackupError(UnknownAppDataFoundError(it))
            }
            .bind()
      }
    }.logFailure(Warn) { "Error reading cloud backup from cloud storage" }

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      // Encode backup to JSON
      val backupEncoded: String =
        when (backup) {
          is CloudBackupV2 -> Json.encodeToStringResult<CloudBackupV2>(backup)
        }
          .mapPossibleRectifiableErrors()
          .bind()

      if (requireAuthRefresh) {
        // Make sure the cloud backup represents an account state that can authenticate.
        authTokensService
          .refreshAccessTokenWithApp(
            backup.f8eEnvironment,
            accountId = accountId,
            scope = AuthTokenScope.Recovery
          )
          .mapError { UnrectifiableCloudBackupError(it) }
          .bind()
      }

      // Write backup to cloud key-value store
      cloudKeyValueStore
        .setString(cloudStoreAccount, cloudBackupKey, backupEncoded)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error writing cloud backup to cloud key-value store" }
        .bind()

      // Save backup locally
      cloudBackupDao
        .set(accountId.serverId, backup)
        .logFailure { "Error saving cloud backup locally" }
        .mapError { UnrectifiableCloudBackupError(it) }
        .bind()
    }

  override suspend fun clear(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      cloudKeyValueStore
        .removeString(cloudStoreAccount, cloudBackupKey)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting cloud backup from cloud key-value store" }
        .bind()

      if (!clearRemoteOnly) {
        cloudBackupDao
          .clear()
          .mapPossibleRectifiableErrors()
          .logFailure(Warn) { "Error deleting local cloud backup" }
          .bind()
      }
    }

  private fun <T> Result<T, Throwable>.mapPossibleRectifiableErrors(): Result<T, CloudBackupError> {
    return mapError { error ->
      when (error) {
        is CloudError -> {
          error.rectificationData
            ?.let { rectificationData ->
              RectifiableCloudBackupError(error, rectificationData)
            }
            ?: UnrectifiableCloudBackupError(error)
        }

        else -> UnrectifiableCloudBackupError(error)
      }
    }
  }
}

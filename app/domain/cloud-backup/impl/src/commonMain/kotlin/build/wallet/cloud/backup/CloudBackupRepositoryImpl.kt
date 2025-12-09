package build.wallet.cloud.backup

import bitkey.auth.AuthTokenScope
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
import build.wallet.logging.logInfo
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class CloudBackupRepositoryImpl(
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudBackupDao: CloudBackupDao,
  private val authTokensService: AuthTokensService,
  private val jsonSerializer: JsonSerializer,
  private val clock: Clock,
) : CloudBackupRepository {
  // Key used to store backups in cloud key-value store
  private val cloudBackupKey = "cloud-backup"

  override suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> = readBackup(cloudStoreAccount, cloudBackupKey)

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      // Encode backup to JSON
      val backupEncoded: String = when (backup) {
        is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(backup)
        is CloudBackupV2 -> jsonSerializer.encodeToStringResult<CloudBackupV2>(backup)
      }.mapPossibleRectifiableErrors()
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

      logInfo { "Cloud backup uploaded successfully: ${backup.hashCode()}" }
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

  override suspend fun archiveBackup(
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      val backupEncoded: String = when (backup) {
        is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(backup)
        is CloudBackupV2 -> jsonSerializer.encodeToStringResult<CloudBackupV2>(backup)
      }.mapPossibleRectifiableErrors()
        .bind()

      val newKey = "$cloudBackupKey-${clock.now()}"

      cloudKeyValueStore
        .setString(cloudStoreAccount, newKey, backupEncoded)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error archiving cloud backup to cloud key-value store" }
        .bind()
    }

  override suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> =
    coroutineBinding {
      val cloudBackupKeys = cloudKeyValueStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()
        .filter { it.startsWith(cloudBackupKey) && it != cloudBackupKey }

      val backups = buildList {
        cloudBackupKeys.forEach { key ->
          val backup = readBackup(cloudStoreAccount, key)
            .mapPossibleRectifiableErrors()
            .bind()
          add(backup)
        }
      }.mapNotNull { it }

      backups
    }

  private suspend fun readBackup(
    cloudStoreAccount: CloudStoreAccount,
    key: String,
  ): Result<CloudBackup?, CloudBackupError> =
    coroutineBinding {
      // Read encoded backup in JSON format, if any
      val backupEncoded: String? = cloudKeyValueStore
        .getString(cloudStoreAccount, key)
        .mapPossibleRectifiableErrors()
        .bind()

      when (backupEncoded) {
        null -> null
        else ->
          // Found encoded app data
          // Attempt to decode as V3 backup, then fall back to V2. See the cloud backup README.md.
          jsonSerializer.decodeFromStringResult<CloudBackupV3>(backupEncoded)
            .orElse { jsonSerializer.decodeFromStringResult<CloudBackupV2>(backupEncoded) }
            .mapError {
              UnrectifiableCloudBackupError(UnknownAppDataFoundError(it))
            }
            .bind()
      }
    }.logFailure(Warn) { "Error reading cloud backup from cloud storage" }

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

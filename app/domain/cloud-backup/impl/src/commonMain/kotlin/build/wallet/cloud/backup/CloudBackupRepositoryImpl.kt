package build.wallet.cloud.backup

import bitkey.auth.AuthTokenScope
import bitkey.serialization.json.JsonEncodingError
import build.wallet.account.AccountService
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.cloud.backup.CloudBackupError.RectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.platform.device.DeviceInfoProvider
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class CloudBackupRepositoryImpl(
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudBackupDao: CloudBackupDao,
  private val authTokensService: AuthTokensService,
  private val jsonSerializer: JsonSerializer,
  private val accountService: AccountService,
  private val sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
  private val clock: Clock,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val cloudBackupRepositoryKeys: CloudBackupRepositoryKeys,
) : CloudBackupRepository {
  // Key used to store backups in cloud key-value store
  private val cloudBackupLegacyKeyPrefix = "cloud-backup"

  override suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> =
    coroutineBinding {
      logInfo { "Reading active backup from cloud storage" }
      var backup: CloudBackup? = null
      if (sharedCloudBackupsFeatureFlag.isEnabled()) {
        // Migrate before reading if the flag is on
        migrateBackupToAccountIdKey(cloudStoreAccount)

        val accountId =
          accountService.activeAccount().firstOrNull()?.accountId
        if (accountId == null) {
          logInfo { "No active account found, skipping backup read" }
          return@coroutineBinding null
        }
        val key = cloudBackupRepositoryKeys.activeBackupFormatAccountSpecificKey(accountId)
        logInfo { "Attempting to read backup with account-specific key: $key" }
        // Try account-specific key first, fall back to legacy
        backup = readThenParseBackup(cloudStoreAccount, key).bind()

        // Verify the backup's account ID matches (defense in depth)
        if (backup != null && backup.accountId != accountId.serverId) {
          logInfo {
            "Found backup but account ID doesn't match (expected: ${accountId.serverId}, found: ${backup.accountId})"
          }
          Err(CloudBackupError.AccountIdMismatched(accountId.serverId, backup.accountId, backup)).bind()
        }
      }

      // If not found in account-specific key, try legacy key (for migration)
      if (backup == null) {
        logInfo { "Attempting to read backup with legacy key: $cloudBackupLegacyKeyPrefix" }
        // Use legacy behavior (read only from "cloud-backup" key)
        // No account ID verification needed since only one backup exists at the legacy key
        backup = readThenParseBackup(cloudStoreAccount, cloudBackupLegacyKeyPrefix).bind()
      }

      if (backup == null) {
        logInfo { "No backup found in cloud storage (tried both account-specific and legacy keys)" }
      }

      backup
    }

  override suspend fun readAllBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> =
    coroutineBinding {
      if (sharedCloudBackupsFeatureFlag.isEnabled()) {
        // Migrate before reading if the flag is on
        migrateBackupToAccountIdKey(cloudStoreAccount)

        // Read all backups
        val allKeys = cloudKeyValueStore
          .keys(cloudStoreAccount)
          .mapPossibleRectifiableErrors()
          .bind()

        // Try to read all valid backups
        val backups = allKeys
          .mapNotNull { key ->
            if (!cloudBackupRepositoryKeys.isValidBackupKey(key)) return@mapNotNull null
            val backupResult = readThenParseBackup(cloudStoreAccount, key)
            if (backupResult.isOk) {
              val backup = backupResult.value
              if (backup != null) {
                logInfo { "Found backup at key: $key (accountId: ${backup.accountId})" }
              }
              backup
            } else {
              // Failed to read this backup, skip it
              logInfo { "Failed to read backup at key $key, skipping." }
              null
            }
          }
        backups
      } else {
        val backupResult = readThenParseBackup(cloudStoreAccount, cloudBackupLegacyKeyPrefix)
        val backups = mutableListOf<CloudBackup>().apply {
          if (backupResult.isOk) {
            val backup = backupResult.value
            if (backup != null) {
              logInfo { "Found backup with legacy key (accountId: ${backup.accountId})" }
              add(backup)
            }
          } else {
            // Failed to read this backup, skip it
            logInfo { "Failed to read backup with legacy key, skipping." }
          }
        }
        backups
      }
    }

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
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

      val backupEncoded: String = serializeBackup(backup).mapPossibleRectifiableErrors()
        .bind()

      val key = cloudBackupRepositoryKeys.activeBackupFormatKey(backup)

      cloudKeyValueStore
        .setString(cloudStoreAccount, key, backupEncoded)
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
    accountId: AccountId?,
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      if (accountId == null) return@coroutineBinding

      // Clear both account-specific and legacy key for backwards compatibility
      val key = cloudBackupRepositoryKeys.activeBackupFormatAccountSpecificKey(accountId)
      cloudKeyValueStore
        .removeString(cloudStoreAccount, key)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting account-specific cloud backup from cloud key-value store" }
        .bind()

      cloudKeyValueStore
        .removeString(cloudStoreAccount, cloudBackupLegacyKeyPrefix)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting legacy cloud backup from cloud key-value store" }
        .bind()

      if (!clearRemoteOnly) {
        cloudBackupDao
          .clear()
          .mapPossibleRectifiableErrors()
          .logFailure(Warn) { "Error deleting local cloud backup" }
          .bind()
      }
    }

  override suspend fun clearAll(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      // Clear both account-specific and legacy key for backwards compatibility
      val allKeys = cloudKeyValueStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      // Delete all backup keys
      allKeys
        .forEach { key ->
          cloudKeyValueStore
            .removeString(cloudStoreAccount, key)
            .mapPossibleRectifiableErrors()
            .logFailure(Warn) { "Error deleting account-specific cloud backup from cloud key-value store" }
            .bind()
        }

      cloudKeyValueStore
        .removeString(cloudStoreAccount, cloudBackupLegacyKeyPrefix)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting legacy cloud backup from cloud key-value store" }
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
      val backupEncoded: String = serializeBackup(backup).mapPossibleRectifiableErrors()
        .bind()

      val newKey = cloudBackupRepositoryKeys.archiveFormatKey(backup)

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
      val allKeys = cloudKeyValueStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      val backups = allKeys
        .mapNotNull { key ->
          if (!cloudBackupRepositoryKeys.isValidArchivedKey(key)) return@mapNotNull null
          val backup: CloudBackup? = readThenParseBackup(cloudStoreAccount, key)
            .mapPossibleRectifiableErrors()
            .bind()
          backup
        }
      backups
    }

  private suspend fun readThenParseBackup(
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
        null -> {
          logInfo { "No backup found at key=$key" }
          null
        }
        else -> {
          // Found encoded app data
          // Attempt to decode as V3 backup, then fall back to V2. See the cloud backup README.md.
          val backup = jsonSerializer.decodeFromStringResult<CloudBackupV3>(backupEncoded)
            .orElse { jsonSerializer.decodeFromStringResult<CloudBackupV2>(backupEncoded) }
            .mapError {
              UnrectifiableCloudBackupError(UnknownAppDataFoundError(it))
            }
            .bind()
          logInfo { "Successfully parsed backup from key=$key (accountId=${backup.accountId})" }
          backup
        }
      }
    }.logFailure(Warn) { "Error reading cloud backup from cloud storage" }

  override suspend fun migrateBackupToAccountIdKey(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      val allKeys = cloudKeyValueStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      val legacyKeys = allKeys.filter { it.startsWith(cloudBackupLegacyKeyPrefix) }

      if (legacyKeys.isNotEmpty()) {
        logInfo { "Migrating legacy backup for keys: ${legacyKeys.joinToString()}" }

        legacyKeys.forEach { key ->
          // Read the legacy backup
          val legacyBackupEncoded = cloudKeyValueStore
            .getString(cloudStoreAccount, key)
            .mapPossibleRectifiableErrors()
            .bind()

          if (legacyBackupEncoded != null) {
            // Parse the backup to ensure it's valid
            val parsedBackup = jsonSerializer.decodeFromStringResult<CloudBackupV2>(legacyBackupEncoded)
              .mapError { UnrectifiableCloudBackupError(UnknownAppDataFoundError(it)) }
              .bind()

            // To make sure everything is type-safe
            val backupAccountSpecific = parsedBackup.mapToAccountSpecific()

            // Write the backup using the account-specific key format.
            if (cloudBackupRepositoryKeys.isValidBackupKey(key)) {
              val accountIdForBackup = if (backupAccountSpecific.isFullAccount()) {
                FullAccountId(backupAccountSpecific.accountId)
              } else {
                LiteAccountId(backupAccountSpecific.accountId)
              }
              writeBackup(
                accountId = accountIdForBackup,
                cloudStoreAccount = cloudStoreAccount,
                backup = backupAccountSpecific,
                requireAuthRefresh = false // Don't require auth refresh for migration
              ).bind()
            } else {
              archiveBackup(
                cloudStoreAccount = cloudStoreAccount,
                backup = backupAccountSpecific
              ).bind()
            }

            // The legacy backup should be removed after successful migration.
            cloudKeyValueStore
              .removeString(cloudStoreAccount, key)
              .mapPossibleRectifiableErrors()
              .logFailure(Warn) { "Error deleting legacy cloud backup from cloud key-value store" }
              .bind()

            logInfo {
              "Successfully migrated legacy backup for key $key to account-specific key"
            }
          }
        }
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

  private fun CloudBackupV2.mapToAccountSpecific() =
    CloudBackupV3(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      delegatedDecryptionKeypair = delegatedDecryptionKeypair,
      appRecoveryAuthKeypair = appRecoveryAuthKeypair,
      fullAccountFields = fullAccountFields,
      isUsingSocRecFakes = isUsingSocRecFakes,
      bitcoinNetworkType = bitcoinNetworkType,
      deviceNickname = deviceInfoProvider.getDeviceInfo().deviceNickname,
      createdAt = clock.now()
    )

  private fun serializeBackup(backup: CloudBackup): Result<String, JsonEncodingError> =
    when (backup) {
      is CloudBackupV2 -> if (sharedCloudBackupsFeatureFlag.isEnabled()) {
        jsonSerializer.encodeToStringResult<CloudBackupV3>(backup.mapToAccountSpecific())
      } else {
        jsonSerializer.encodeToStringResult<CloudBackupV2>(backup)
      }
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(backup)
    }
}

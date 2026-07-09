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
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class CloudBackupServiceImpl(
  private val cloudBackupStore: CloudBackupStore,
  private val cloudBackupDao: CloudBackupDao,
  private val authTokensService: AuthTokensService,
  private val jsonSerializer: JsonSerializer,
  private val accountService: AccountService,
  private val sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
  private val clock: Clock,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupService {
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
        val key = cloudBackupStoreKeys.activeBackupFormatAccountSpecificKey(accountId)
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
        val allKeys = cloudBackupStore
          .keys(cloudStoreAccount)
          .mapPossibleRectifiableErrors()
          .bind()

        // Try to read all valid backups
        val backups = allKeys
          .mapNotNull { key ->
            if (!cloudBackupStoreKeys.isValidBackupKey(key)) return@mapNotNull null
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
        backups.freshestByAccount()
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
        backups.freshestByAccount()
      }
    }

  override suspend fun readBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> =
    coroutineBinding {
      val accountSpecificKey = cloudBackupStoreKeys.activeBackupFormatAccountSpecificKey(accountId)
      val accountSpecificBackup = readThenParseBackup(cloudStoreAccount, accountSpecificKey).bind()
      val legacyBackup = readThenParseBackup(cloudStoreAccount, cloudBackupLegacyKeyPrefix).bind()

      listOfNotNull(accountSpecificBackup, legacyBackup)
        .freshestByAccount()
        .firstOrNull()
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

      val backupEncoded: ByteString = serializeBackup(backup).mapPossibleRectifiableErrors()
        .bind()

      val key = cloudBackupStoreKeys.activeBackupFormatKey(backup)

      cloudBackupStore
        .set(cloudStoreAccount, key, backupEncoded)
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
      val key = cloudBackupStoreKeys.activeBackupFormatAccountSpecificKey(accountId)
      cloudBackupStore
        .remove(cloudStoreAccount, key)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting account-specific cloud backup from cloud key-value store" }
        .bind()

      cloudBackupStore
        .remove(cloudStoreAccount, cloudBackupLegacyKeyPrefix)
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
      val allKeys = cloudBackupStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      // Delete known backup keys only (active + archived; account-specific + legacy formats).
      allKeys
        .filter { key ->
          cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)
        }
        .forEach { key ->
          cloudBackupStore
            .remove(cloudStoreAccount, key)
            .mapPossibleRectifiableErrors()
            .logFailure(Warn) { "Error deleting account-specific cloud backup from cloud key-value store" }
            .bind()
        }

      cloudBackupStore
        .remove(cloudStoreAccount, cloudBackupLegacyKeyPrefix)
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
      val backupEncoded: ByteString = serializeBackup(backup).mapPossibleRectifiableErrors()
        .bind()

      val newKey = cloudBackupStoreKeys.archiveFormatKey(backup)

      cloudBackupStore
        .set(cloudStoreAccount, newKey, backupEncoded)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error archiving cloud backup to cloud key-value store" }
        .bind()
    }

  override suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> =
    coroutineBinding {
      val allKeys = cloudBackupStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      val backups = allKeys
        .mapNotNull { key ->
          if (!cloudBackupStoreKeys.isValidArchivedKey(key)) return@mapNotNull null
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
      val backupEncoded = cloudBackupStore
        .get(cloudStoreAccount, key)
        .mapPossibleRectifiableErrors()
        .bind()

      when (backupEncoded) {
        null -> {
          logInfo { "No backup found at key=$key" }
          null
        }
        else -> {
          val backup = parseBackup(backupEncoded).bind()
          logInfo { "Successfully parsed backup from key=$key (accountId=${backup.accountId})" }
          backup
        }
      }
    }.logFailure(Warn) { "Error reading cloud backup from cloud storage" }

  override suspend fun migrateBackupToAccountIdKey(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      val allKeys = cloudBackupStore
        .keys(cloudStoreAccount)
        .mapPossibleRectifiableErrors()
        .bind()

      val legacyKeys = allKeys.filter { it.startsWith(cloudBackupLegacyKeyPrefix) }

      if (legacyKeys.isNotEmpty()) {
        logInfo { "Migrating legacy backup for keys: ${legacyKeys.joinToString()}" }

        legacyKeys.forEach { key ->
          migrateLegacyBackupKey(cloudStoreAccount, key).bind()
        }
      }
    }

  private suspend fun migrateLegacyBackupKey(
    cloudStoreAccount: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      val legacyBackup = readThenParseBackup(cloudStoreAccount, key).bind()
        ?: return@coroutineBinding
      val backupAccountSpecific = legacyBackup.toAccountSpecificBackup()

      val shouldRemoveLegacyBackup = if (cloudBackupStoreKeys.isValidBackupKey(key)) {
        migrateLegacyActiveBackup(
          cloudStoreAccount = cloudStoreAccount,
          key = key,
          legacyBackup = legacyBackup,
          accountSpecificBackup = backupAccountSpecific
        ).bind()
      } else {
        archiveBackup(
          cloudStoreAccount = cloudStoreAccount,
          backup = backupAccountSpecific
        ).bind()
        true
      }

      if (shouldRemoveLegacyBackup) {
        removeLegacyBackupKey(cloudStoreAccount, key).bind()
      }
    }

  private suspend fun migrateLegacyActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
    key: String,
    legacyBackup: CloudBackup,
    accountSpecificBackup: CloudBackup,
  ): Result<Boolean, CloudBackupError> =
    coroutineBinding {
      val accountIdForBackup = accountSpecificBackup.accountIdForBackup()
      val accountSpecificKey = cloudBackupStoreKeys
        .activeBackupFormatAccountSpecificKey(accountIdForBackup)
      val existingAccountSpecificBackup = accountSpecificBackupAtKeyForMigration(
        cloudStoreAccount = cloudStoreAccount,
        accountSpecificKey = accountSpecificKey
      ).bind()

      if (
        existingAccountSpecificBackup == null ||
        shouldWriteAccountSpecificBackup(legacyBackup, accountSpecificBackup, existingAccountSpecificBackup)
      ) {
        writeBackup(
          accountId = accountIdForBackup,
          cloudStoreAccount = cloudStoreAccount,
          backup = accountSpecificBackup,
          requireAuthRefresh = false // Don't require auth refresh for migration
        ).bind()
        true
      } else {
        shouldRemoveLegacyActiveBackup(
          key = key,
          legacyBackup = legacyBackup,
          accountSpecificBackup = accountSpecificBackup,
          existingAccountSpecificBackup = existingAccountSpecificBackup
        )
      }
    }

  private suspend fun accountSpecificBackupAtKeyForMigration(
    cloudStoreAccount: CloudStoreAccount,
    accountSpecificKey: String,
  ): Result<CloudBackup?, CloudBackupError> =
    coroutineBinding {
      val existingAccountSpecificBackupResult = readThenParseBackup(
        cloudStoreAccount = cloudStoreAccount,
        key = accountSpecificKey
      )
      if (existingAccountSpecificBackupResult.isOk) {
        return@coroutineBinding existingAccountSpecificBackupResult.value
      }

      val error = existingAccountSpecificBackupResult.error
      if (error.isUnknownAppDataFound()) {
        logInfo {
          "Overwriting unreadable account-specific backup at key $accountSpecificKey " +
            "from legacy backup"
        }
        null
      } else {
        existingAccountSpecificBackupResult.bind()
      }
    }

  private fun shouldWriteAccountSpecificBackup(
    legacyBackup: CloudBackup,
    accountSpecificBackup: CloudBackup,
    existingAccountSpecificBackup: CloudBackup,
  ): Boolean =
    existingAccountSpecificBackup.accountId != accountSpecificBackup.accountId ||
      legacyBackup.isFresherThan(existingAccountSpecificBackup)

  private fun shouldRemoveLegacyActiveBackup(
    key: String,
    legacyBackup: CloudBackup,
    accountSpecificBackup: CloudBackup,
    existingAccountSpecificBackup: CloudBackup,
  ): Boolean =
    if (
      existingAccountSpecificBackup == legacyBackup ||
      existingAccountSpecificBackup == accountSpecificBackup ||
      existingAccountSpecificBackup.isFresherThan(legacyBackup)
    ) {
      logInfo {
        "Skipping legacy active backup migration for key $key because account-specific backup is current"
      }
      true
    } else {
      logInfo {
        "Preserving legacy active backup for key $key because account-specific backup has equal freshness"
      }
      false
    }

  private suspend fun removeLegacyBackupKey(
    cloudStoreAccount: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudBackupError> =
    coroutineBinding {
      cloudBackupStore
        .remove(cloudStoreAccount, key)
        .mapPossibleRectifiableErrors()
        .logFailure(Warn) { "Error deleting legacy cloud backup from cloud key-value store" }
        .bind()

      logInfo {
        "Successfully migrated legacy backup for key $key to account-specific key"
      }
    }

  private fun parseBackup(backupEncoded: ByteString): Result<CloudBackup, CloudBackupError> =
    jsonSerializer.decodeCloudBackup(backupEncoded.utf8())
      .mapError {
        UnrectifiableCloudBackupError(UnknownAppDataFoundError(it))
      }

  private fun CloudBackupError.isUnknownAppDataFound(): Boolean =
    this is UnrectifiableCloudBackupError && cause is UnknownAppDataFoundError

  private fun CloudBackup.accountIdForBackup(): AccountId =
    if (isFullAccount()) {
      FullAccountId(accountId)
    } else {
      LiteAccountId(accountId)
    }

  private fun CloudBackup.toAccountSpecificBackup(): CloudBackup =
    when (this) {
      is CloudBackupV2 -> mapToAccountSpecific()
      is CloudBackupV3 -> this
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

  private fun serializeBackup(backup: CloudBackup): Result<ByteString, JsonEncodingError> =
    when (backup) {
      is CloudBackupV2 -> if (sharedCloudBackupsFeatureFlag.isEnabled()) {
        jsonSerializer.encodeToStringResult<CloudBackupV3>(backup.mapToAccountSpecific())
      } else {
        jsonSerializer.encodeToStringResult<CloudBackupV2>(backup)
      }
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(backup)
    }.map { it.encodeUtf8() }
}

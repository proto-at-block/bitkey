package build.wallet.cloud.backup.migration

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.backup.UnknownAppDataFoundError
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.UbiquitousKeyValueStore
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.orElse
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.encodeUtf8

/**
 * Writes cloud backups to CloudKit for existing accounts.
 *
 * When the CloudKit feature flag is enabled, we transition from iCloud Key-Value Store (KVS)
 * to CloudKit as the primary cloud backup storage on iOS. This service handles that transition
 * by creating a fresh active backup and uploading it to CloudKit, plus copying archived KVS
 * backup keys to CloudKit when missing there.
 *
 * This is iOS-only and gated by [IosCloudKitBackupFeatureFlag] in the worker.
 */
interface CloudKitBackupMigrationService {
  /**
   * Writes backup to CloudKit if not already present.
   *
   * Skips if:
   * - No active account
   * - No signed-in iCloud account
   * - CloudKit already has a backup
   * - No local backup exists
   * - Full account backup is missing required fields
   */
  suspend fun migrateIfNeeded(): Result<Unit, Throwable>
}

@BitkeyInject(AppScope::class)
class CloudKitBackupMigrationServiceImpl(
  private val accountService: AccountService,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupService: CloudBackupService,
  private val cloudBackupDao: CloudBackupDao,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val ubiquitousKeyValueStore: UbiquitousKeyValueStore,
  private val jsonSerializer: JsonSerializer,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator,
) : CloudKitBackupMigrationService {
  override suspend fun migrateIfNeeded(): Result<Unit, Throwable> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
        ?: return@coroutineBinding
      val cloudStoreAccount = cloudStoreAccountRepository
        .currentAccount(cloudServiceProvider())
        .bind()
        ?: return@coroutineBinding

      // Order matters: copy archived backups first, even if active backup migration is skipped.
      // This preserves historical backups while active backup state is checked separately below.
      val iCloudStoreAccount = cloudStoreAccount as? iCloudAccount
      if (iCloudStoreAccount != null) {
        migrateArchivedBackupsIfNeeded(iCloudStoreAccount).onFailure { error ->
          logWarn(throwable = error) {
            "CloudKit archived backup migration failed; continuing active backup migration"
          }
        }
      }

      val hasExistingCloudKitBackup = hasExistingCloudKitBackupForActiveAccount(
        activeAccountId = account.accountId,
        cloudStoreAccount = cloudStoreAccount
      ).getOrElse { error ->
        logWarn(throwable = error) {
          "CloudKit active-backup presence check failed; continuing migration write"
        }
        false
      }
      if (hasExistingCloudKitBackup) {
        return@coroutineBinding
      }

      val newBackup = when (account) {
        is FullAccount -> {
          val localBackup = cloudBackupDao.get(account.accountId.serverId).bind()
          if (localBackup == null) {
            return@coroutineBinding
          }
          val sealedCsek = when (localBackup) {
            is CloudBackupV2 -> localBackup.fullAccountFields?.sealedHwEncryptionKey
            is CloudBackupV3 -> localBackup.fullAccountFields?.sealedHwEncryptionKey
          }
          if (sealedCsek == null) {
            logWarn { "CloudKit backup skipped: full account backup missing required fields" }
            return@coroutineBinding
          }
          fullAccountCloudBackupCreator.create(account.keybox, sealedCsek).bind()
        }
        is LiteAccount -> liteAccountCloudBackupCreator.create(account).bind()
        else -> return@coroutineBinding
      }

      logInfo {
        "CloudKit active backup migration started for account [${account.accountId.serverId}]"
      }
      cloudBackupService.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudStoreAccount,
        backup = newBackup,
        requireAuthRefresh = false
      ).bind()

      logInfo {
        "CloudKit active backup migration completed for account [${account.accountId.serverId}]"
      }
    }.logFailure { "CloudKit backup migration failed" }

  /**
   * Copies archived backups from KVS to CloudKit when missing in CloudKit.
   *
   * This intentionally migrates both key formats detected by
   * [CloudBackupStoreKeys.isValidArchivedKey]:
   * - legacy keys (`cloud-backup-<timestamp>`)
   * - account-specific/shared keys (`cb-<account-id>-<timestamp>`)
   *
   * This is intentionally not gated by the shared-cloud-backups flag.
   * We always migrate archived keys for backward compatibility when shared cloud backups are
   * enabled later.
   */
  private suspend fun migrateArchivedBackupsIfNeeded(
    cloudStoreAccount: iCloudAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val archivedKvsKeys = ubiquitousKeyValueStore.keys(cloudStoreAccount).bind()
        .filter(cloudBackupStoreKeys::isValidArchivedKey)
      if (archivedKvsKeys.isEmpty()) return@coroutineBinding

      val cloudKitKeys = cloudKitKeyValueStore.keys(cloudStoreAccount).bind()
      val archivedKeysToMigrate = archivedKvsKeys.filterNot(cloudKitKeys::contains)
      if (archivedKeysToMigrate.isEmpty()) return@coroutineBinding

      logInfo {
        "CloudKit archived backup migration started (keys=${archivedKeysToMigrate.size})"
      }
      archivedKeysToMigrate.forEach { key ->
        val value = ubiquitousKeyValueStore.getString(cloudStoreAccount, key).bind()
        if (value != null) {
          cloudKitKeyValueStore.set(cloudStoreAccount, key, value.encodeUtf8()).bind()
        }
      }

      logInfo {
        "CloudKit archived backup migration completed (keys=${archivedKeysToMigrate.size})"
      }
    }

  /**
   * Checks CloudKit directly to avoid treating KVS fallback reads as migrated CloudKit state.
   *
   * This protects migration correctness when CloudKit is empty but legacy KVS still has the
   * active backup.
   */
  private suspend fun hasExistingCloudKitBackupForActiveAccount(
    activeAccountId: AccountId,
    cloudStoreAccount: build.wallet.cloud.store.CloudStoreAccount,
  ): Result<Boolean, Throwable> =
    coroutineBinding {
      val iCloudStoreAccount = cloudStoreAccount as? iCloudAccount ?: run {
        val existingBackup = cloudBackupService
          .readActiveBackup(cloudStoreAccount)
          .bind()
        if (existingBackup != null) {
          if (existingBackup.accountId == activeAccountId.serverId) {
            return@coroutineBinding true
          }
          logInfo {
            "Cloud backup account mismatch: existing=${existingBackup.accountId}, " +
              "active=${activeAccountId.serverId}. Continuing migration."
          }
        }
        return@coroutineBinding false
      }

      val cloudKitBackupKeys = cloudKitKeyValueStore.keys(iCloudStoreAccount).bind()
        .filter(cloudBackupStoreKeys::isValidBackupKey)
      if (cloudKitBackupKeys.isEmpty()) return@coroutineBinding false

      val hasAccountSpecificActiveCloudKitBackupKey = cloudKitBackupKeys.any { key ->
        cloudBackupStoreKeys.isAccountSpecificActiveBackupKeyForAccount(key, activeAccountId)
      }
      if (hasAccountSpecificActiveCloudKitBackupKey) {
        return@coroutineBinding true
      }

      // Legacy active key is shared across accounts, so key presence alone is insufficient.
      // Validate payload ownership before treating it as migrated active state.
      val legacyActiveCloudKitKey = cloudKitBackupKeys.firstOrNull(cloudBackupStoreKeys::isLegacyActiveBackupKey)
        ?: return@coroutineBinding false
      val legacyCloudKitBackupMatchesActiveAccount = doesCloudKitBackupAtKeyBelongToAccount(
        cloudStoreAccount = iCloudStoreAccount,
        key = legacyActiveCloudKitKey,
        accountId = activeAccountId
      ).bind()
      if (legacyCloudKitBackupMatchesActiveAccount) {
        return@coroutineBinding true
      }

      logInfo {
        "CloudKit backup key mismatch for active account [${activeAccountId.serverId}]; " +
          "continuing migration."
      }
      false
    }

  private suspend fun doesCloudKitBackupAtKeyBelongToAccount(
    cloudStoreAccount: iCloudAccount,
    key: String,
    accountId: AccountId,
  ): Result<Boolean, Throwable> =
    coroutineBinding {
      val backupEncoded = cloudKitKeyValueStore.get(cloudStoreAccount, key).bind()?.utf8()
        ?: return@coroutineBinding false

      val backup = parseCloudBackup(backupEncoded).bind()
      backup.accountId == accountId.serverId
    }

  private fun parseCloudBackup(backupEncoded: String): Result<CloudBackup, Throwable> {
    return jsonSerializer.decodeFromStringResult<CloudBackupV3>(backupEncoded)
      .orElse { jsonSerializer.decodeFromStringResult<CloudBackupV2>(backupEncoded) }
      .mapError { UnknownAppDataFoundError(it) }
  }
}

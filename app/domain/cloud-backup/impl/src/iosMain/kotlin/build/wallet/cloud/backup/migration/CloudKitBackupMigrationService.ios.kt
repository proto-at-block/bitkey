package build.wallet.cloud.backup.migration

import build.wallet.account.AccountService
import build.wallet.bitkey.account.Account
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
import build.wallet.cloud.backup.decodeCloudBackup
import build.wallet.cloud.backup.freshest
import build.wallet.cloud.backup.isFresherThan
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.UbiquitousKeyValueStore
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.encodeUtf8

/**
 * Writes cloud backups to CloudKit for existing accounts.
 *
 * When the CloudKit feature flag is enabled, we transition from iCloud Key-Value Store (KVS)
 * to CloudKit as the primary cloud backup storage on iOS. This service handles that transition
 * by creating a fresh active backup and uploading it to CloudKit when missing, reconciling
 * same-account CloudKit drift against fresher KVS backups, plus copying archived KVS
 * backup keys to CloudKit when missing there.
 *
 * This is iOS-only and gated by [IosCloudKitBackupFeatureFlag] in the worker.
 */
interface CloudKitBackupMigrationService {
  /**
   * Writes backup to CloudKit if not already present or if CloudKit differs from the current
   * freshest same-account backup available in CloudKit or KVS.
   *
   * Skips if:
   * - No active account
   * - No signed-in iCloud account
   * - CloudKit already has the freshest same-account backup
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
  private val sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
  private val cloudKitBackupMigrationStatusDao: CloudKitBackupMigrationStatusDao,
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

      if (iCloudStoreAccount != null) {
        // Keep this after archived migration so archived-copy failures can retry on later launches
        // without regenerating the active backup once that reconciliation has succeeded.
        val alreadyReconciled = cloudKitBackupMigrationStatusDao
          .isReconciled(account.accountId, iCloudStoreAccount)
          .bind()
        if (alreadyReconciled) {
          val needsReconciliation = iCloudActiveBackupNeedsReconciliation(
            activeAccountId = account.accountId,
            cloudStoreAccount = iCloudStoreAccount
          ).bind()
          if (!needsReconciliation) return@coroutineBinding
        }

        val didReconcile = reconcileICloudActiveBackup(account, iCloudStoreAccount).bind()
        if (didReconcile) {
          cloudKitBackupMigrationStatusDao
            .setReconciled(account.accountId, iCloudStoreAccount)
            .bind()
        }
      } else {
        migrateNonICloudActiveBackupIfNeeded(account, cloudStoreAccount).bind()
      }
    }.logFailure { "CloudKit backup migration failed" }

  private suspend fun migrateNonICloudActiveBackupIfNeeded(
    account: Account,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val existingBackup = cloudBackupService
        .readActiveBackup(cloudStoreAccount)
        .bind()
      if (existingBackup != null) {
        if (existingBackup.accountId != account.accountId.serverId) {
          logInfo {
            "Cloud backup account mismatch: existing=${existingBackup.accountId}, " +
              "active=${account.accountId.serverId}. Continuing migration."
          }
        } else {
          return@coroutineBinding
        }
      }

      val newBackup = currentGeneratedBackup(account).bind() ?: return@coroutineBinding
      writeActiveBackup(
        account = account,
        cloudStoreAccount = cloudStoreAccount,
        backup = newBackup
      ).bind()
    }

  private suspend fun reconcileICloudActiveBackup(
    account: Account,
    cloudStoreAccount: iCloudAccount,
  ): Result<Boolean, Throwable> =
    coroutineBinding {
      val existingBackup = directCloudKitActiveBackupForAccount(
        activeAccountId = account.accountId,
        cloudStoreAccount = cloudStoreAccount
      ).getOrElse { error ->
        logWarn(throwable = error) {
          "CloudKit active-backup presence check failed; continuing reconciliation write"
        }
        null
      }

      if (existingBackup != null) {
        val kvsBackups = directKvsActiveBackupsForAccount(
          activeAccountId = account.accountId,
          cloudStoreAccount = cloudStoreAccount
        ).getOrElse { error ->
          logWarn(throwable = error) {
            "KVS active-backup presence check failed; preserving existing CloudKit backup."
          }
          emptyList()
        }
        val fresherKvsBackup = kvsBackups
          .filter { kvsBackup -> kvsBackup.isFresherThan(existingBackup) }
          .freshest()
        if (fresherKvsBackup == null) {
          logInfo {
            "CloudKit active backup is at least as fresh as KVS for account " +
              "[${account.accountId.serverId}]. " +
              "Preserving CloudKit."
          }
          return@coroutineBinding true
        } else {
          logInfo {
            "KVS active backup is fresher than CloudKit for account " +
              "[${account.accountId.serverId}]. " +
              "Rewriting CloudKit."
          }
        }

        writeActiveBackup(
          account = account,
          cloudStoreAccount = cloudStoreAccount,
          backup = fresherKvsBackup
        ).bind()

        verifyDirectCloudKitBackup(
          cloudStoreAccount = cloudStoreAccount,
          backup = fresherKvsBackup
        ).bind()

        return@coroutineBinding true
      }

      val newBackup = currentGeneratedBackup(account).bind() ?: return@coroutineBinding false

      writeActiveBackup(
        account = account,
        cloudStoreAccount = cloudStoreAccount,
        backup = newBackup
      ).bind()

      verifyDirectCloudKitBackup(
        cloudStoreAccount = cloudStoreAccount,
        backup = newBackup
      ).bind()

      true
    }

  private suspend fun currentGeneratedBackup(
    account: Account,
  ): Result<CloudBackup?, Throwable> =
    coroutineBinding {
      when (account) {
        is FullAccount -> currentGeneratedFullAccountBackup(account).bind()
        is LiteAccount -> liteAccountCloudBackupCreator.create(account).bind()
        else -> null
      }
    }

  private suspend fun currentGeneratedFullAccountBackup(
    account: FullAccount,
  ): Result<CloudBackup?, Throwable> =
    coroutineBinding {
      val localBackup = cloudBackupDao.get(account.accountId.serverId).bind()
        ?: return@coroutineBinding null
      val sealedCsek = when (localBackup) {
        is CloudBackupV2 -> localBackup.fullAccountFields?.sealedHwEncryptionKey
        is CloudBackupV3 -> localBackup.fullAccountFields?.sealedHwEncryptionKey
      }
      if (sealedCsek == null) {
        logWarn { "CloudKit backup skipped: full account backup missing required fields" }
        return@coroutineBinding null
      }
      fullAccountCloudBackupCreator.create(account.keybox, sealedCsek).bind()
    }

  private suspend fun writeActiveBackup(
    account: Account,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      logInfo {
        "CloudKit active backup migration started for account [${account.accountId.serverId}]"
      }
      cloudBackupService.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudStoreAccount,
        backup = backup,
        requireAuthRefresh = false
      ).bind()

      logInfo {
        "CloudKit active backup migration completed for account [${account.accountId.serverId}]"
      }
    }

  /**
   * Reads CloudKit directly to avoid treating KVS fallback data as current CloudKit state.
   */
  private suspend fun directCloudKitActiveBackupForAccount(
    activeAccountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<CloudBackup?, Throwable> =
    coroutineBinding {
      val cloudKitBackupKeys = cloudKitKeyValueStore.keys(cloudStoreAccount).bind()
        .filter(cloudBackupStoreKeys::isValidBackupKey)
      if (cloudKitBackupKeys.isEmpty()) return@coroutineBinding null

      val accountSpecificKey = cloudBackupStoreKeys
        .activeBackupFormatAccountSpecificKey(activeAccountId)
      val keysToRead = activeBackupKeysToRead(cloudKitBackupKeys, accountSpecificKey)

      val backups = mutableListOf<CloudBackup>()
      keysToRead.forEach { key ->
        val backup = readDirectCloudKitBackupAtKey(cloudStoreAccount, key)
          .getOrElse { error ->
            logWarn(throwable = error) {
              "CloudKit active backup read failed at key [$key]; continuing reconciliation."
            }
            null
          }
        if (backup?.accountId == activeAccountId.serverId) {
          backups += backup
        } else if (backup != null) {
          logInfo {
            "CloudKit backup account mismatch: existing=${backup.accountId}, " +
              "active=${activeAccountId.serverId}. Continuing reconciliation."
          }
        }
      }

      backups.freshest()
    }

  private suspend fun directKvsActiveBackupsForAccount(
    activeAccountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<List<CloudBackup>, Throwable> =
    coroutineBinding {
      val kvsBackupKeys = ubiquitousKeyValueStore.keys(cloudStoreAccount).bind()
        .filter(cloudBackupStoreKeys::isValidBackupKey)
      if (kvsBackupKeys.isEmpty()) return@coroutineBinding emptyList()

      val accountSpecificKey = cloudBackupStoreKeys
        .activeBackupFormatAccountSpecificKey(activeAccountId)
      val keysToRead = activeBackupKeysToRead(kvsBackupKeys, accountSpecificKey)

      val backups = mutableListOf<CloudBackup>()
      keysToRead.forEach { key ->
        val backup = readDirectKvsBackupAtKey(cloudStoreAccount, key)
          .getOrElse { error ->
            logWarn(throwable = error) {
              "KVS active backup read failed at key [$key]; continuing reconciliation."
            }
            null
          }
        if (backup?.accountId == activeAccountId.serverId) {
          backups += backup
        } else if (backup != null) {
          logInfo {
            "KVS backup account mismatch: existing=${backup.accountId}, " +
              "active=${activeAccountId.serverId}. Continuing reconciliation."
          }
        }
      }

      backups
    }

  private fun activeBackupKeysToRead(
    availableBackupKeys: Collection<String>,
    accountSpecificKey: String,
  ): List<String> {
    val legacyActiveKey = availableBackupKeys
      .firstOrNull(cloudBackupStoreKeys::isLegacyActiveBackupKey)

    return if (sharedCloudBackupsFeatureFlag.isEnabled()) {
      listOfNotNull(
        accountSpecificKey.takeIf(availableBackupKeys::contains),
        legacyActiveKey
      ).distinct()
    } else {
      listOfNotNull(legacyActiveKey)
    }
  }

  private suspend fun iCloudActiveBackupNeedsReconciliation(
    activeAccountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Boolean, Throwable> =
    coroutineBinding {
      val cloudKitBackup = directCloudKitActiveBackupForAccount(
        activeAccountId = activeAccountId,
        cloudStoreAccount = cloudStoreAccount
      ).bind()
      val kvsBackups = directKvsActiveBackupsForAccount(
        activeAccountId = activeAccountId,
        cloudStoreAccount = cloudStoreAccount
      ).bind()

      when {
        cloudKitBackup == null -> true
        kvsBackups.any { kvsBackup ->
          kvsBackup.accountId == cloudKitBackup.accountId && kvsBackup.isFresherThan(cloudKitBackup)
        } -> true
        else -> false
      }
    }

  private suspend fun verifyDirectCloudKitBackup(
    cloudStoreAccount: iCloudAccount,
    backup: CloudBackup,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val writtenKey = cloudBackupStoreKeys.activeBackupFormatKey(backup)
      val verifiedBackup = readDirectCloudKitBackupAtKey(cloudStoreAccount, writtenKey).bind()
      if (verifiedBackup != backup) {
        logWarn {
          "CloudKit active backup read-back verification failed for key [$writtenKey]"
        }
        Err(CloudKitBackupVerificationError()).bind<Unit>()
      }
    }

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

  private suspend fun readDirectCloudKitBackupAtKey(
    cloudStoreAccount: iCloudAccount,
    key: String,
  ): Result<CloudBackup?, Throwable> =
    coroutineBinding {
      val backupEncoded = cloudKitKeyValueStore.get(cloudStoreAccount, key).bind()
        ?: return@coroutineBinding null

      parseCloudBackup(backupEncoded.utf8()).bind()
    }

  private suspend fun readDirectKvsBackupAtKey(
    cloudStoreAccount: iCloudAccount,
    key: String,
  ): Result<CloudBackup?, Throwable> =
    coroutineBinding {
      val backupEncoded = ubiquitousKeyValueStore.getString(cloudStoreAccount, key).bind()
        ?: return@coroutineBinding null

      parseCloudBackup(backupEncoded).bind()
    }

  private fun parseCloudBackup(backupEncoded: String): Result<CloudBackup, Throwable> {
    return jsonSerializer.decodeCloudBackup(backupEncoded)
      .mapError { UnknownAppDataFoundError(it) }
  }

  private class CloudKitBackupVerificationError : Error(
    "CloudKit backup write succeeded but direct read-back did not match."
  )
}

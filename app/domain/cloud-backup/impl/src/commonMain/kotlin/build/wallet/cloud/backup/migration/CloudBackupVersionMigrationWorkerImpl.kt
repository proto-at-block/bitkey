package build.wallet.cloud.backup.migration

import build.wallet.account.AccountService
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.backup.isLatestVersion
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class CloudBackupVersionMigrationWorkerImpl(
  private val accountService: AccountService,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator,
  appSessionManager: AppSessionManager,
) : CloudBackupVersionMigrationWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(
      // Skip if backgrounded. We'll start a new attempt when foregrounded.
      backgroundStrategy = BackgroundStrategy.Skip
    ),
    RunStrategy.OnEvent(
      observer = appSessionManager.appSessionState
        .filter { it == AppSessionState.FOREGROUND },
      backgroundStrategy = BackgroundStrategy.Skip
    )
  )

  override suspend fun executeWork() {
    val account = accountService.activeAccount().first()

    if (account != null) {
      checkAndMigrateBackup(account)
    }
  }

  private suspend fun checkAndMigrateBackup(account: Account) {
    // Get cloud store account first
    val cloudStoreAccount = cloudStoreAccountRepository
      .currentAccount(cloudServiceProvider())
      .get()
      ?: return // No cloud store account available

    // Read the active backup from cloud storage
    val localBackup = cloudBackupRepository
      .readActiveBackup(cloudStoreAccount)
      .get()
      ?: return // No backup to migrate

    // Check if backup is already on latest version
    if (localBackup.isLatestVersion) {
      return // Already up to date
    }

    logInfo {
      "Detected outdated backup schema (${localBackup::class.simpleName}), initiating migration to latest version"
    }

    // Migrate based on account type
    when (account) {
      is FullAccount -> migrateAccountBackup(account, localBackup, cloudStoreAccount)
      is LiteAccount -> migrateAccountBackup(account, localBackup, cloudStoreAccount)
      else -> {
        // No migration needed for other account types
        logInfo { "Skipping migration for unsupported account type: ${account::class.simpleName}" }
      }
    }
  }

  private suspend fun migrateAccountBackup(
    account: Account,
    oldBackup: CloudBackup,
    cloudStoreAccount: CloudStoreAccount,
  ) {
    // Only V2 backups need migration currently
    if (oldBackup !is CloudBackupV2) {
      return
    }

    // For full accounts, validate and extract sealed CSEK
    if (account is FullAccount) {
      val fullAccountFields = oldBackup.fullAccountFields
      if (fullAccountFields == null) {
        logInfo { "Skipping migration: ${oldBackup::class.simpleName} backup has no full account fields" }
        return
      }
    }

    migrateBackupToLatest(
      account = account,
      oldBackup = oldBackup,
      cloudStoreAccount = cloudStoreAccount,
      createNewBackup = {
        when (account) {
          is FullAccount -> fullAccountCloudBackupCreator.create(
            keybox = account.keybox,
            sealedCsek = oldBackup.fullAccountFields!!.sealedHwEncryptionKey
          )
          is LiteAccount -> liteAccountCloudBackupCreator.create(account = account)
          else -> error("Unsupported account type: ${account::class.simpleName}")
        }
      }
    ).logFailure {
      "Failed to migrate ${account::class.simpleName} backup from ${oldBackup::class.simpleName} to latest version"
    }
  }

  private suspend fun migrateBackupToLatest(
    account: Account,
    oldBackup: CloudBackup,
    cloudStoreAccount: CloudStoreAccount,
    createNewBackup: suspend () -> Result<CloudBackup, Error>,
  ): Result<Unit, Error> =
    coroutineBinding {
      // Create new backup using the provided creator
      val newBackup = createNewBackup().bind()

      // Upload new backup to cloud
      cloudBackupRepository.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudStoreAccount,
        backup = newBackup,
        requireAuthRefresh = true
      ).bind()

      logInfo {
        "Successfully migrated ${account::class.simpleName} backup from ${oldBackup::class.simpleName} to ${newBackup::class.simpleName}"
      }
    }
}

package build.wallet.wallet.migration

import bitkey.recovery.DescriptorBackupService
import build.wallet.account.AccountService
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.isPrivateWallet
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.feature.flags.PrivateWalletMigrationBalanceThresholdFeatureFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.sweep.SweepService
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import build.wallet.wallet.migration.PrivateWalletMigrationError.FeeEstimationFailed
import build.wallet.wallet.migration.PrivateWalletMigrationError.InsufficientFundsForMigration
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class PrivateWalletMigrationServiceImpl(
  private val keyGenerator: AppKeysGenerator,
  private val createKeysetClient: CreateAccountKeysetV2F8eClient,
  private val uuidGenerator: UuidGenerator,
  privateWalletMigrationFeatureFlag: PrivateWalletMigrationFeatureFlag,
  privateWalletMigrationBalanceThresholdFeatureFlag:
    PrivateWalletMigrationBalanceThresholdFeatureFlag,
  private val accountService: AccountService,
  private val cloudBackupDao: CloudBackupDao,
  private val keyboxDao: KeyboxDao,
  private val privateWalletMigrationDao: PrivateWalletMigrationDao,
  private val ssekDao: SsekDao,
  private val descriptorBackupService: DescriptorBackupService,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  private val bitcoinWalletService: BitcoinWalletService,
  private val sweepService: SweepService,
) : PrivateWalletMigrationService {
  override val migrationState: Flow<PrivateWalletMigrationState> = combine(
    privateWalletMigrationDao.currentState(),
    privateWalletMigrationFeatureFlag.flagValue().map { it.value },
    privateWalletMigrationBalanceThresholdFeatureFlag.flagValue().map { it.value },
    accountService.activeAccount().map { it as? FullAccount }
  ) { databaseState, flagEnabled, balanceThreshold, account ->
    val state = databaseState.get()
    val newHardwareKey = state?.newHardwareKey
    val newAppKey = state?.newAppKey
    val newServerKey = state?.newServerKey
    val keysetLocalId = state?.keysetLocalId

    when {
      !flagEnabled -> PrivateWalletMigrationState.NotAvailable.also {
        logInfo { "Private wallet migration not enabled" }
      }
      databaseState.isErr -> PrivateWalletMigrationState.NotAvailable.also {
        databaseState.logFailure {
          "Private wallet migration unavailable to load because of a database error"
        }
      }
      account == null -> PrivateWalletMigrationState.NotAvailable.also {
        logInfo { "Private wallet not available without full account" }
      }
      state == null && account.keybox.activeSpendingKeyset.f8eSpendingKeyset.isPrivateWallet ->
        PrivateWalletMigrationState.NotAvailable.also {
          logInfo { "Account already has a private wallet" }
        }
      newHardwareKey == null -> {
        if (balanceThreshold < 0) {
          // If the threshold is <0, consider the threshold disabled and allow migration
          logInfo { "Threshold is negative, migration is available" }
          PrivateWalletMigrationState.Available
        } else {
          val thresholdAmount = BitcoinMoney.sats(balanceThreshold.toLong())
          val balance = bitcoinWalletService.getTransactionData().balance.total
          when {
            balance > thresholdAmount -> PrivateWalletMigrationState.NotAvailable.also {
              logInfo {
                "Wallet balance ($balance) exceeds migration threshold ($thresholdAmount)"
              }
            }
            else -> PrivateWalletMigrationState.Available
          }
        }
      }
      newAppKey == null -> PrivateWalletMigrationState.InKeysetCreation.HwKeyCreated(
        newHwKeys = newHardwareKey
      )
      newServerKey == null || keysetLocalId == null ->
        PrivateWalletMigrationState.InKeysetCreation.AppKeyCreated(
          newHwKeys = newHardwareKey,
          newAppKeys = newAppKey
        )
      !state.descriptorBackupCompleted -> PrivateWalletMigrationState.InKeysetCreation.LocalKeyboxActivated(
        keyset = SpendingKeyset(
          localId = keysetLocalId,
          networkType = account.keybox.config.bitcoinNetworkType,
          appKey = newAppKey,
          hardwareKey = newHardwareKey,
          f8eSpendingKeyset = newServerKey
        )
      )
      !state.serverKeysetActivated -> PrivateWalletMigrationState.DescriptorBackupCompleted(
        newKeyset = SpendingKeyset(
          localId = keysetLocalId,
          networkType = account.keybox.config.bitcoinNetworkType,
          appKey = newAppKey,
          hardwareKey = newHardwareKey,
          f8eSpendingKeyset = newServerKey
        )
      )
      !state.cloudBackupCompleted -> PrivateWalletMigrationState.ServerKeysetActivated(
        updatedKeybox = account.keybox,
        sealedCsek = account.accountId.serverId
          .let { cloudBackupDao.get(it) }
          .get()
          .let {
            when (it) {
              is CloudBackupV2 ->
                it.fullAccountFields
                  ?.sealedHwEncryptionKey
              null -> null
            }
          },
        newKeyset = SpendingKeyset(
          localId = keysetLocalId,
          networkType = account.keybox.config.bitcoinNetworkType,
          appKey = newAppKey,
          hardwareKey = newHardwareKey,
          f8eSpendingKeyset = newServerKey
        )
      )
      !state.sweepCompleted -> PrivateWalletMigrationState.CloudBackupCompleted(
        updatedKeybox = account.keybox,
        newKeyset = SpendingKeyset(
          localId = keysetLocalId,
          networkType = account.keybox.config.bitcoinNetworkType,
          appKey = newAppKey,
          hardwareKey = newHardwareKey,
          f8eSpendingKeyset = newServerKey
        )
      )
      else -> PrivateWalletMigrationState.NotAvailable.also {
        logInfo { "Migration completed" }
      }
    }
  }.distinctUntilChanged()

  override suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
    ssek: Sek,
    sealedSsek: SealedSsek,
  ): Result<PrivateWalletMigrationState.InitiationComplete, PrivateWalletMigrationError> {
    return coroutineBinding {
      migrationState
        .takeWhile { state ->
          state !is PrivateWalletMigrationState.InitiationComplete
        }
        .collect { state ->
          when (state) {
            PrivateWalletMigrationState.Available -> {
              privateWalletMigrationDao.saveHardwareKey(newHwKeys.spendingKey)
                .mapError { PrivateWalletMigrationError.KeysetCreationFailed(it) }
                .onSuccess { logInfo { "Saved new hardware key" } }
                .bind()
            }

            is PrivateWalletMigrationState.InKeysetCreation.HwKeyCreated -> {
              generateAppKey()
                .mapError { PrivateWalletMigrationError.KeysetCreationFailed(it) }
                .onSuccess { logInfo { "Saved new app key" } }
                .bind()
            }

            is PrivateWalletMigrationState.InKeysetCreation.AppKeyCreated -> {
              createServerKeyAndActivate(
                account = account,
                newHwKey = state.newHwKeys,
                newAppKey = state.newAppKeys,
                proofOfPossession = proofOfPossession
              )
                .onSuccess { logInfo { "Keyset Activated" } }
                .bind()
            }

            is PrivateWalletMigrationState.InKeysetCreation.LocalKeyboxActivated -> {
              updateDescriptorBackup(
                hwProofOfPossession = proofOfPossession,
                newKeyset = state.keyset,
                sealedSsek = sealedSsek,
                ssek = ssek
              )
                .onSuccess { logInfo { "Backups Created" } }
                .bind()
            }

            is PrivateWalletMigrationState.DescriptorBackupCompleted -> {
              activateServerKeyset(
                account = account,
                proofOfPossession = proofOfPossession,
                serverKeyset = state.newKeyset.f8eSpendingKeyset
              )
                .onSuccess { logInfo { "Server Keyset Activated" } }
                .bind()
            }

            is PrivateWalletMigrationState.ServerKeysetActivated -> {
              logDebug { "Awaiting Cloud Backup" }
            }
            is PrivateWalletMigrationState.CloudBackupCompleted -> {
              logDebug { "Migration completed in state ${state::class.simpleName}. Ready for sweep" }
            }
            is PrivateWalletMigrationState.NotAvailable -> {
              Err(IllegalStateException("Can't start unavailable migration"))
                .mapError { PrivateWalletMigrationError.KeysetCreationFailed(it) }
                .bind()
            }
          }
        }

      migrationState
        .filterIsInstance<PrivateWalletMigrationState.InitiationComplete>()
        .first()
    }
  }

  override suspend fun completeCloudBackup(): Result<Unit, PrivateWalletMigrationError> {
    return privateWalletMigrationDao.setCloudBackupComplete()
      .mapError { PrivateWalletMigrationError.CloudBackupFailed(it) }
  }

  override suspend fun estimateMigrationFees(
    account: FullAccount,
  ): Result<BitcoinMoney, PrivateWalletMigrationError> {
    return sweepService.estimateSweepWithMockDestination(account.keybox)
      .map { sweep -> sweep.totalFeeAmount }
      .mapError { error ->
        when (error) {
          is NoFundsToSweep -> InsufficientFundsForMigration
          is SweepGenerationFailed -> FeeEstimationFailed(error.cause)
        }
      }
  }

  override suspend fun completeMigration(): Result<Unit, PrivateWalletMigrationError> {
    return coroutineBinding {
      privateWalletMigrationDao.setSweepCompleted()
        .mapError { PrivateWalletMigrationError.MigrationCompletionFailed(it) }
        .bind()
    }
  }

  override suspend fun clearMigration() {
    privateWalletMigrationDao.clear()
  }

  private suspend fun generateAppKey(): Result<AppSpendingPublicKey, Throwable> {
    return coroutineBinding {
      keyGenerator.generateKeyBundle()
        .bind()
        .spendingKey
        .also { key ->
          privateWalletMigrationDao.saveAppKey(key).bind()
        }
    }
  }

  private suspend fun createServerKeyAndActivate(
    account: FullAccount,
    newHwKey: HwSpendingPublicKey,
    newAppKey: AppSpendingPublicKey,
    proofOfPossession: HwFactorProofOfPossession,
  ): Result<SpendingKeyset, PrivateWalletMigrationError> {
    return coroutineBinding {
      val serverKeyset = createKeysetClient.createKeyset(
        f8eEnvironment = account.keybox.config.f8eEnvironment,
        fullAccountId = account.accountId,
        hardwareSpendingKey = newHwKey,
        appSpendingKey = newAppKey,
        network = account.keybox.config.bitcoinNetworkType,
        appAuthKey = account.keybox.activeAppKeyBundle.authKey,
        hardwareProofOfPossession = proofOfPossession
      )
        .mapError { PrivateWalletMigrationError.KeysetServerActivationFailed(it) }
        .bind()

      val newKeyset = SpendingKeyset(
        localId = uuidGenerator.random(),
        networkType = account.keybox.config.bitcoinNetworkType,
        appKey = newAppKey,
        hardwareKey = newHwKey,
        f8eSpendingKeyset = serverKeyset
      )

      keyboxDao.saveKeyboxAsActive(
        account.keybox.withNewSpendingKeyset(newKeyset)
      )
        .mapError { PrivateWalletMigrationError.KeysetActivationFailed(it) }
        .bind()

      privateWalletMigrationDao.saveServerKey(
        serverKey = newKeyset.f8eSpendingKeyset
      )
        .mapError { PrivateWalletMigrationError.KeysetActivationFailed(it) }
        .bind()

      privateWalletMigrationDao.saveKeysetLocalId(
        keysetLocalId = newKeyset.localId
      )
        .mapError { PrivateWalletMigrationError.KeysetActivationFailed(it) }
        .bind()

      newKeyset
    }
  }

  /**
   * Mark the keyset on the server as active.
   */
  private suspend fun activateServerKeyset(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    serverKeyset: F8eSpendingKeyset,
  ): Result<Unit, PrivateWalletMigrationError> {
    return coroutineBinding {
      setActiveSpendingKeysetF8eClient.set(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        keysetId = serverKeyset.keysetId,
        appAuthKey = account.keybox.activeAppKeyBundle.authKey,
        hwFactorProofOfPossession = proofOfPossession
      )
        .mapError { PrivateWalletMigrationError.KeysetActivationFailed(it) }
        .bind()

      privateWalletMigrationDao.setServerKeysetActive()
        .mapError { PrivateWalletMigrationError.KeysetActivationFailed(it) }
        .bind()
    }
  }

  private suspend fun updateDescriptorBackup(
    hwProofOfPossession: HwFactorProofOfPossession,
    newKeyset: SpendingKeyset,
    sealedSsek: SealedSsek,
    ssek: Sek,
  ): Result<Unit, PrivateWalletMigrationError> {
    return coroutineBinding {
      val updatedAccount = accountService.activeAccount()
        .filterNotNull()
        .filterIsInstance<FullAccount>()
        .first { it.keybox.activeSpendingKeyset.localId == newKeyset.localId }

      val serverKeysets = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = updatedAccount.config.f8eEnvironment,
        fullAccountId = updatedAccount.accountId
      )
        .mapError { PrivateWalletMigrationError.DescriptorBackupFailed(it) }
        .bind()
        .keysets
        .filterIsInstance<LegacyRemoteKeyset>()
        .toSpendingKeysets(uuidGenerator)

      val missingKeysets = serverKeysets
        .filter {
          it.f8eSpendingKeyset.keysetId !in updatedAccount.keybox.keysets.map {
            it.f8eSpendingKeyset.keysetId
          }
        }

      val updatedKeybox = updatedAccount.keybox.copy(
        keysets = updatedAccount.keybox.keysets + missingKeysets,
        canUseKeyboxKeysets = true
      )

      keyboxDao.saveKeyboxAsActive(updatedKeybox)
        .mapError { PrivateWalletMigrationError.DescriptorBackupFailed(it) }
        .bind()

      ssekDao.set(sealedSsek, ssek)
        .mapError { PrivateWalletMigrationError.DescriptorBackupFailed(it) }
        .bind()

      val keysetsToBackup: List<SpendingKeyset> = serverKeysets + newKeyset
      val extraKeysets = (updatedKeybox.keysets - keysetsToBackup)
      if (extraKeysets.isNotEmpty()) {
        logWarn { "${extraKeysets.size} extra keysets found locally in keybox that were not in F8e" }
      }

      descriptorBackupService.uploadDescriptorBackups(
        accountId = updatedAccount.accountId,
        sealedSsekForDecryption = null,
        sealedSsekForEncryption = sealedSsek,
        appAuthKey = updatedKeybox.activeAppKeyBundle.authKey,
        hwKeyProof = hwProofOfPossession,
        descriptorsToDecrypt = listOf(),
        keysetsToEncrypt = keysetsToBackup
      )
        .mapError { PrivateWalletMigrationError.DescriptorBackupFailed(it) }
        .bind()

      privateWalletMigrationDao.setDescriptorBackupComplete()
        .mapError { PrivateWalletMigrationError.DescriptorBackupFailed(it) }
        .bind()
    }
  }
}

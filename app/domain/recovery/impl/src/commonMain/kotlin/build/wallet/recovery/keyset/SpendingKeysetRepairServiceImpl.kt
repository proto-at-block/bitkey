package build.wallet.recovery.keyset

import bitkey.recovery.DescriptorBackupService
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.feature.flags.KeysetRepairFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logDebug
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.platform.random.UuidGenerator
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Implementation of [SpendingKeysetRepairService] that manages keyset sync detection and repair
 * from stale cloud backup recovery.
 *
 * This service runs as a worker on startup and when app returns to foreground to detect
 * keyset mismatches. When a mismatch is detected, the repair process can be initiated.
 *
 * The repair process is idempotent by design:
 * 1. Fetch keysets from server
 * 2. Decrypt private keysets if needed (using descriptor backups)
 * 3. Create and upload cloud backup FIRST (before local update)
 * 4. Update local keybox (the "commit" point)
 *
 * If anything fails before step 4, the local keybox still has the wrong active keyset,
 * so the sync check will still detect a mismatch and the user can retry.
 */
@BitkeyInject(AppScope::class)
class SpendingKeysetRepairServiceImpl(
  private val accountService: AccountService,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val keysetRepairFeatureFlag: KeysetRepairFeatureFlag,
  private val descriptorBackupService: DescriptorBackupService,
  private val keyboxDao: KeyboxDao,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val uuidGenerator: UuidGenerator,
  private val appKeysGenerator: AppKeysGenerator,
  private val createAccountKeysetV2F8eClient: CreateAccountKeysetV2F8eClient,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  appSessionManager: AppSessionManager,
) : SpendingKeysetRepairService, KeysetRepairWorker {
  private val _syncStatus =
    MutableStateFlow<SpendingKeysetSyncStatus>(SpendingKeysetSyncStatus.Synced)
  override val syncStatus: StateFlow<SpendingKeysetSyncStatus> = _syncStatus

  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.OnEvent(
      observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
    ),
    RunStrategy.OnEvent(keysetRepairFeatureFlag.flagValue())
  )

  override suspend fun executeWork() {
    _syncStatus.value = checkSyncStatus()
  }

  private suspend fun checkSyncStatus(): SpendingKeysetSyncStatus {
    if (!keysetRepairFeatureFlag.isEnabled()) {
      return SpendingKeysetSyncStatus.Synced
    }

    val account = when (val accountStatus = accountService.accountStatus().first().get()) {
      is AccountStatus.ActiveAccount -> accountStatus.account as? FullAccount
      else -> null
    }

    if (account == null) {
      logDebug { "No active full account, skipping keyset sync check" }
      return SpendingKeysetSyncStatus.Synced
    }

    val localActiveKeysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

    // Fetch keysets from server to get the active keyset ID
    return listKeysetsF8eClient.listKeysets(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.keybox.fullAccountId
    ).mapBoth(
      success = { response ->
        val serverActiveKeysetId = response.activeKeysetId
        if (localActiveKeysetId == serverActiveKeysetId) {
          return@mapBoth SpendingKeysetSyncStatus.Synced
        }

        logWarn {
          "Keyset mismatch detected: local keyset $localActiveKeysetId does not match server active keyset $serverActiveKeysetId"
        }
        if (keysetRepairFeatureFlag.isEnabled()) {
          SpendingKeysetSyncStatus.Mismatch(
            localActiveKeysetId = localActiveKeysetId,
            serverActiveKeysetId = serverActiveKeysetId
          )
        } else {
          logInfo { "Not showing keyset mismatch notification to user due to feature flag disabled" }
          SpendingKeysetSyncStatus.Synced
        }
      },
      failure = { SpendingKeysetSyncStatus.Unknown(it) }
    )
  }

  override suspend fun checkPrivateKeysets(
    account: FullAccount,
  ): Result<PrivateKeysetInfo, KeysetRepairError> =
    coroutineBinding {
      val response = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.keybox.fullAccountId
      )
        .mapError { KeysetRepairError.FetchKeysetsFailed(cause = it) }
        .bind()

      val cachedData = KeysetRepairCachedData(
        response = response,
        serverActiveKeysetId = response.activeKeysetId
      )

      val wrappedSsek = response.wrappedSsek
      if (response.descriptorBackups.isNotEmpty() && wrappedSsek != null) {
        PrivateKeysetInfo.NeedsUnsealing(
          cachedResponseData = cachedData
        )
      } else {
        PrivateKeysetInfo.None(cachedResponseData = cachedData)
      }
    }

  override suspend fun attemptRepair(
    account: FullAccount,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    coroutineBinding {
      logInfo { "Starting keyset repair for account ${account.accountId}" }

      // Use cached response from checkPrivateKeysets to avoid duplicate network call
      val response = cachedData.response
      val sealedSsek = response.wrappedSsek

      // 1. Decrypt private keysets using descriptor backups if they exist
      // If there are descriptor backups, we should use them instead of the keysets directly
      val keysets = if (response.descriptorBackups.isNotEmpty() && sealedSsek != null) {
        descriptorBackupService.unsealDescriptors(
          sealedSsek = sealedSsek,
          encryptedDescriptorBackups = response.descriptorBackups
        )
          .mapError { KeysetRepairError.DecryptKeysetsFailed(cause = it) }
          .bind()
      } else {
        response.keysets
          .filterIsInstance<LegacyRemoteKeyset>()
          .toSpendingKeysets(uuidGenerator)
      }

      // 2. Find the server's active keyset from the cached account status
      val serverActiveKeysetId = cachedData.serverActiveKeysetId

      val serverActiveKeyset = keysets.find {
        it.f8eSpendingKeyset.keysetId == serverActiveKeysetId
      } ?: Err(
        KeysetRepairError.FetchKeysetsFailed(
          cause = IllegalStateException("Server active keyset not found in keyset list")
        )
      ).bind()

      logInfo { "Found server active keyset: $serverActiveKeysetId" }

      // 3. Build updated keybox (not saved yet!)
      val updatedKeybox = account.keybox.copy(
        activeSpendingKeyset = serverActiveKeyset,
        keysets = keysets,
        canUseKeyboxKeysets = true
      )

      // 4. Create and upload cloud backup FIRST (before updating local keybox)
      // This ensures idempotency: if we crash after this but before saving the keybox,
      // the local keybox still has the wrong active keyset, so detection
      // will still find a mismatch and we can retry.
      val sealedCsek = getCloudBackupSealedCsek() ?: Err(
        KeysetRepairError.CloudBackupFailed(
          cause = IllegalStateException("No sealed CSEK available for cloud backup")
        )
      ).bind()

      val backup = fullAccountCloudBackupCreator.create(
        keybox = updatedKeybox,
        sealedCsek = sealedCsek
      )
        .recoverIf(
          predicate = {
            val isFieldError = it is FullAccountFieldsCreationError
            isFieldError && it.cause?.cause is MissingActivePrivateKeyError
          },
          transform = {
            Err(
              KeysetRepairError.MissingPrivateKeyForActiveKeyset(
                cause = it,
                updatedKeybox = updatedKeybox
              )
            ).bind<CloudBackup>()
          }
        )
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()

      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()
        ?: Err(
          KeysetRepairError.CloudBackupFailed(
            cause = IllegalStateException("No cloud account available")
          )
        ).bind()

      cloudBackupRepository.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudAccount,
        backup = backup,
        requireAuthRefresh = false
      )
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()

      logInfo { "Cloud backup updated successfully" }

      // 5. NOW update local keybox (the "commit" point)
      keyboxDao.saveKeyboxAsActive(updatedKeybox)
        .mapError { KeysetRepairError.SaveKeyboxFailed(cause = it) }
        .bind()

      logInfo { "Local keybox updated successfully" }

      // 6. Update sync status
      markRepaired()

      logInfo { "Keyset repair completed successfully" }

      KeysetRepairState.RepairComplete(updatedKeybox)
    }

  override suspend fun regenerateActiveKeyset(
    account: FullAccount,
    updatedKeybox: Keybox,
    hwSpendingKey: HwSpendingPublicKey,
    hwProofOfPossession: HwFactorProofOfPossession,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    coroutineBinding {
      logInfo { "Regenerating active keyset for account ${account.accountId}" }

      val appKeyBundle = appKeysGenerator.generateKeyBundle()
        .mapError { KeysetRepairError.SaveKeyboxFailed(cause = it) }
        .bind()

      logInfo { "Generated new app spending key" }

      val f8eSpendingKeyset = createAccountKeysetV2F8eClient.createKeyset(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        hardwareSpendingKey = hwSpendingKey,
        appSpendingKey = appKeyBundle.spendingKey,
        network = account.keybox.config.bitcoinNetworkType,
        appAuthKey = account.keybox.activeAppKeyBundle.authKey,
        hardwareProofOfPossession = hwProofOfPossession
      )
        .mapError { KeysetRepairError.FetchKeysetsFailed(cause = it) }
        .bind()

      logInfo { "Created new keyset on server: ${f8eSpendingKeyset.keysetId}" }

      val newKeyset = SpendingKeyset(
        localId = uuidGenerator.random(),
        networkType = account.keybox.config.bitcoinNetworkType,
        appKey = appKeyBundle.spendingKey,
        hardwareKey = hwSpendingKey,
        f8eSpendingKeyset = f8eSpendingKeyset
      )

      val keyboxWithNewKeyset = updatedKeybox.copy(
        activeSpendingKeyset = newKeyset,
        keysets = updatedKeybox.keysets + newKeyset,
        canUseKeyboxKeysets = true
      )

      keyboxDao.saveKeyboxAsActive(keyboxWithNewKeyset)
        .mapError { KeysetRepairError.SaveKeyboxFailed(cause = it) }
        .bind()

      logInfo { "Local keybox updated with new keyset" }

      // Get the sealed SSEK from the cached response
      val sealedSsek = cachedData.response.wrappedSsek
      if (sealedSsek != null) {
        // Get existing encrypted descriptors from the cached response
        val existingDescriptors = cachedData.response.descriptorBackups

        // Get all keysets to backup (existing legacy keysets + new keyset)
        val serverKeysets = listKeysetsF8eClient.listKeysets(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId
        )
          .mapError { KeysetRepairError.DescriptorBackupFailed(cause = it) }
          .bind()
          .keysets
          .filterIsInstance<LegacyRemoteKeyset>()
          .toSpendingKeysets(uuidGenerator)

        val keysetsToBackup: List<SpendingKeyset> = serverKeysets + newKeyset

        descriptorBackupService.uploadDescriptorBackups(
          accountId = account.accountId,
          sealedSsekForDecryption = sealedSsek,
          sealedSsekForEncryption = sealedSsek,
          appAuthKey = keyboxWithNewKeyset.activeAppKeyBundle.authKey,
          hwKeyProof = hwProofOfPossession,
          descriptorsToDecrypt = existingDescriptors,
          keysetsToEncrypt = keysetsToBackup
        )
          .mapError { KeysetRepairError.DescriptorBackupFailed(cause = it) }
          .bind()

        logInfo { "Descriptor backup uploaded successfully" }
      } else {
        logInfo { "No sealed SSEK available, skipping descriptor backup" }
      }

      setActiveSpendingKeysetF8eClient.set(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        keysetId = f8eSpendingKeyset.keysetId,
        appAuthKey = keyboxWithNewKeyset.activeAppKeyBundle.authKey,
        hwFactorProofOfPossession = hwProofOfPossession
      )
        .mapError { KeysetRepairError.KeysetActivationFailed(cause = it) }
        .bind()

      logInfo { "Keyset activated on server" }

      val sealedCsek = getCloudBackupSealedCsek() ?: Err(
        KeysetRepairError.CloudBackupFailed(
          cause = IllegalStateException("No sealed CSEK available for cloud backup")
        )
      ).bind()

      val backup = fullAccountCloudBackupCreator.create(
        keybox = keyboxWithNewKeyset,
        sealedCsek = sealedCsek
      )
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()

      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()
        ?: Err(
          KeysetRepairError.CloudBackupFailed(
            cause = IllegalStateException("No cloud account available")
          )
        ).bind()

      cloudBackupRepository.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudAccount,
        backup = backup,
        requireAuthRefresh = false
      )
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()

      logInfo { "Cloud backup updated successfully with regenerated keyset" }

      markRepaired()

      logInfo { "Keyset regeneration completed successfully" }

      KeysetRepairState.RepairComplete(keyboxWithNewKeyset)
    }

  private fun markRepaired() {
    logInfo { "Marking keyset as repaired" }
    _syncStatus.value = SpendingKeysetSyncStatus.Synced
  }

  private suspend fun getCloudBackupSealedCsek(): SealedCsek? {
    val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .get() ?: return null

    val backup = cloudBackupRepository.readActiveBackup(cloudAccount)
      .get() ?: return null

    return backup.fullAccountFields?.sealedHwEncryptionKey
  }
}

/**
 * Extension property to access fullAccountFields from CloudBackup
 */
private val CloudBackup.fullAccountFields
  get() = when (this) {
    is CloudBackupV2 -> fullAccountFields
    is CloudBackupV3 -> fullAccountFields
  }

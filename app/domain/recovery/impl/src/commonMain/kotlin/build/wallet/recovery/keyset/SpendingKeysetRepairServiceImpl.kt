package build.wallet.recovery.keyset

import bitkey.account.HardwareType
import bitkey.recovery.DescriptorBackupService
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingKeyProof
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
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
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
  private val cloudBackupService: CloudBackupService,
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
          if (account.keybox.isPrivateWallet && !account.keybox.canUseKeyboxKeysets) {
            logWarn {
              "Incomplete private wallet keybox detected for active keyset $localActiveKeysetId"
            }
            return@mapBoth SpendingKeysetSyncStatus.IncompletePrivateWallet(
              activeKeysetId = localActiveKeysetId
            )
          }

          if (account.keybox.canUseKeyboxKeysets) {
            val localKeysetIds = account.keybox.keysets
              .map { it.f8eSpendingKeyset.keysetId }
              .toSet()
            val serverKeysetIds = response.keysets
              .map { it.keysetId }
              .toSet()
            val missingServerKeysetIds = serverKeysetIds - localKeysetIds

            if (missingServerKeysetIds.isNotEmpty()) {
              // Only advertise the repair flow when EVERY missing keyset is recoverable.
              // determineRepairDataSource aborts the repair if any private keyset can be neither
              // reused from local state nor unsealed from a backup, so a mixed set (say one legacy
              // keyset plus one unbacked private keyset) would surface an actionable banner for a
              // repair that always terminates. The customer closes the terminal screen, the banner
              // is still there, and they run the flow again -- the loop this status prevents.
              //
              // A descriptor backup is only usable if we also hold the wrapped SSEK needed to
              // unseal it; without one, unsealDescriptors cannot run.
              val backedUpKeysetIds = when (response.wrappedSsek) {
                null -> emptySet()
                else -> response.descriptorBackups.map { it.keysetId }.toSet()
              }
              val unrecoverableKeysetIds = response.keysets
                .filter { it.keysetId in missingServerKeysetIds }
                .filterNot { it is LegacyRemoteKeyset || it.keysetId in backedUpKeysetIds }
                .map { it.keysetId }
                .toSet()

              if (unrecoverableKeysetIds.isNotEmpty()) {
                logWarn {
                  "Incomplete keyset list detected for active keyset $localActiveKeysetId, but " +
                    "${unrecoverableKeysetIds.size} missing keyset(s) cannot be recovered " +
                    "(no usable descriptor backup): ${unrecoverableKeysetIds.joinToString()}; " +
                    "all missing server keysets: ${missingServerKeysetIds.joinToString()}"
                }
                return@mapBoth SpendingKeysetSyncStatus.IncompleteKeysetListUnrecoverable(
                  activeKeysetId = localActiveKeysetId,
                  missingKeysetIds = missingServerKeysetIds
                )
              }

              logWarn {
                "Incomplete keyset list detected for active keyset $localActiveKeysetId; missing server keysets: ${missingServerKeysetIds.joinToString()}"
              }
              return@mapBoth SpendingKeysetSyncStatus.IncompleteKeysetList(
                activeKeysetId = localActiveKeysetId,
                missingKeysetIds = missingServerKeysetIds
              )
            }
          }

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

      when (determineRepairDataSource(account, response).bind()) {
        RepairDataSource.DescriptorBackups -> {
          PrivateKeysetInfo.NeedsUnsealing(
            cachedResponseData = cachedData
          )
        }

        // No SSEK unsealing needed for these, so no hardware tap is required.
        RepairDataSource.DirectFromResponse,
        RepairDataSource.LocalPrivateKeysets,
        RepairDataSource.LegacyOnly -> {
          PrivateKeysetInfo.None(cachedResponseData = cachedData)
        }
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

      // 1. Resolve the full keyset list from server data and, when required,
      // descriptor backups for inactive private keysets.
      val keysets = when (determineRepairDataSource(account, response).bind()) {
        RepairDataSource.LegacyOnly -> {
          logInfo { "Repairing wallet using legacy keysets from listKeysets" }
          response.keysets
            .filterIsInstance<LegacyRemoteKeyset>()
            .toSpendingKeysets(uuidGenerator)
        }

        RepairDataSource.DirectFromResponse -> {
          logInfo { "Repairing wallet directly from listKeysets response" }
          buildKeysetsDirectlyFromResponse(account, response).bind()
        }

        RepairDataSource.DescriptorBackups -> {
          logInfo { "Repairing wallet using descriptor backups for private keysets" }
          val sealedSsek = response.wrappedSsek ?: Err(
            KeysetRepairError.FetchKeysetsFailed(
              cause = IllegalStateException("Descriptor backups require a wrapped SSEK")
            )
          ).bind()

          val unsealedKeysets = descriptorBackupService.unsealDescriptors(
            sealedSsek = sealedSsek,
            encryptedDescriptorBackups = response.descriptorBackups
          )
            .mapError { KeysetRepairError.DecryptKeysetsFailed(cause = it) }
            .bind()

          // Descriptor backups only cover keysets that have one. Resolve every server keyset
          // individually so legacy keysets (which carry full descriptors in the listKeysets
          // response) and already-known local keysets are not dropped just because this
          // account also has private keysets. Without this, accounts with a mix of keyset
          // types silently repair to an incomplete list and re-detect immediately.
          resolveKeysetsPerKeyset(
            account = account,
            response = response,
            unsealedKeysets = unsealedKeysets
          )
        }

        RepairDataSource.LocalPrivateKeysets -> {
          logInfo { "Repairing wallet using private keysets already held locally" }
          // Nothing to unseal: every private keyset the server lists is already local, so
          // resolveKeysetsPerKeyset reuses those and rebuilds any legacy keysets from the response.
          resolveKeysetsPerKeyset(
            account = account,
            response = response,
            unsealedKeysets = emptyList()
          )
        }
      }

      // 2. Refuse to proceed unless the resolved keyset list covers every keyset the server knows
      // about.
      //
      // This MUST happen before any cloud backup or local keybox write: a partial repair would
      // otherwise replace the customer's existing cloud backup with a strictly smaller keyset set.
      // If that backup held the only recoverable copy of a keyset local state had lost, overwriting
      // it converts a supportable state into permanent fund inaccessibility on the next recovery.
      //
      // It must also happen before the active-keyset lookup below. When the server's active keyset
      // is itself unresolvable, that lookup fails with a generic FetchKeysetsFailed, which the UI
      // presents as a retryable error -- sending the customer back through a flow that can never
      // succeed. Classifying unresolvable keysets first yields the terminal support state instead.
      val resolvedKeysetIds = keysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
      val unresolvedKeysetIds = response.keysets.map { it.keysetId }.toSet() - resolvedKeysetIds
      if (unresolvedKeysetIds.isNotEmpty()) {
        logWarn {
          "Aborting keyset repair before any writes; could not resolve all server keysets. " +
            "Unresolved: ${unresolvedKeysetIds.joinToString()}"
        }
        Err(
          KeysetRepairError.UnresolvableKeysets(unresolvedKeysetIds = unresolvedKeysetIds)
        ).bind<Unit>()
      }

      // 3. Find the server's active keyset from the cached account status. Safe to treat a miss as
      // a generic failure here: the unresolved check above has already ruled out the case where the
      // active keyset is absent because it could not be reconstructed.
      val serverActiveKeysetId = cachedData.serverActiveKeysetId

      val serverActiveKeyset = keysets.find {
        it.f8eSpendingKeyset.keysetId == serverActiveKeysetId
      } ?: Err(
        KeysetRepairError.FetchKeysetsFailed(
          cause = IllegalStateException("Server active keyset not found in keyset list")
        )
      ).bind()

      logInfo { "Found server active keyset: $serverActiveKeysetId" }

      // 4. Build updated keybox (not saved yet!)
      val updatedKeybox = account.keybox.copy(
        activeSpendingKeyset = serverActiveKeyset,
        keysets = keysets,
        canUseKeyboxKeysets = true
      )

      // 5. Create and upload cloud backup FIRST (before updating local keybox)
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

      cloudBackupService.writeBackup(
        accountId = account.accountId,
        cloudStoreAccount = cloudAccount,
        backup = backup,
        requireAuthRefresh = false
      )
        .mapError { KeysetRepairError.CloudBackupFailed(cause = it) }
        .bind()

      logInfo { "Cloud backup updated successfully" }

      // 6. NOW update local keybox (the "commit" point)
      keyboxDao.saveKeyboxAsActive(updatedKeybox)
        .mapError { KeysetRepairError.SaveKeyboxFailed(cause = it) }
        .bind()

      logInfo { "Local keybox updated successfully" }

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
    hwSpendingKeyProof: HwSpendingKeyProof?,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    coroutineBinding {
      if (account.config.hardwareType == HardwareType.W3) {
        Err(
          KeysetRepairError.KeysetActivationFailed(
            cause = IllegalStateException("W3 keyset repair requires action proofs")
          )
        ).bind<Unit>()
      }

      val proof = PrivilegedActionProof.HwKeyProof(hwProofOfPossession)
      val prepared = prepareRegeneratedActiveKeyset(
        account = account,
        updatedKeybox = updatedKeybox,
        hwSpendingKey = hwSpendingKey,
        hwSpendingKeyProof = hwSpendingKeyProof
      ).bind()

      completeRegeneratedActiveKeyset(
        account = account,
        preparedRegeneratedKeyset = prepared,
        descriptorBackupProof = proof,
        keysetActivationProof = proof,
        cachedData = cachedData
      ).bind()
    }

  override suspend fun prepareRegeneratedActiveKeyset(
    account: FullAccount,
    updatedKeybox: Keybox,
    hwSpendingKey: HwSpendingPublicKey,
    hwSpendingKeyProof: HwSpendingKeyProof?,
  ): Result<PreparedRegeneratedKeyset, KeysetRepairError> =
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
        hardwareSpendingKeyProof = hwSpendingKeyProof
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

      PreparedRegeneratedKeyset(
        keybox = keyboxWithNewKeyset,
        newKeyset = newKeyset
      )
    }

  override suspend fun completeRegeneratedActiveKeyset(
    account: FullAccount,
    preparedRegeneratedKeyset: PreparedRegeneratedKeyset,
    descriptorBackupProof: PrivilegedActionProof?,
    keysetActivationProof: PrivilegedActionProof,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError> =
    coroutineBinding {
      logInfo {
        "Completing regenerated keyset repair for account ${account.accountId}"
      }

      val keyboxWithNewKeyset = preparedRegeneratedKeyset.keybox
      val newKeyset = preparedRegeneratedKeyset.newKeyset

      // Get the sealed SSEK from the cached response
      val sealedSsek = cachedData.response.wrappedSsek
      if (sealedSsek != null) {
        val proof = descriptorBackupProof ?: Err(
          KeysetRepairError.DescriptorBackupFailed(
            cause = IllegalStateException("Descriptor backup upload requires authorization proof")
          )
        ).bind()

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
          proof = proof,
          descriptorsToDecrypt = existingDescriptors,
          keysetsToEncrypt = keysetsToBackup
        )
          .mapError { KeysetRepairError.DescriptorBackupFailed(cause = it) }
          .bind()

        logInfo { "Descriptor backup uploaded successfully" }
      } else {
        logInfo { "No sealed SSEK available, skipping descriptor backup" }
      }

      val signedKeysetVerificationResponse = setActiveSpendingKeysetF8eClient.set(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        keysetId = newKeyset.f8eSpendingKeyset.keysetId,
        appAuthKey = keyboxWithNewKeyset.activeAppKeyBundle.authKey,
        proof = keysetActivationProof
      )
        .mapError { KeysetRepairError.KeysetActivationFailed(cause = it) }
        .bind()

      val signedKeysetVerification = when (account.config.hardwareType) {
        HardwareType.W3 -> signedKeysetVerificationResponse ?: run {
          Err(
            KeysetRepairError.KeysetActivationFailed(
              cause = IllegalStateException(
                "W3 keyset activation did not return signed keyset verification data"
              )
            )
          ).bind()
        }
        HardwareType.W1 -> signedKeysetVerificationResponse
      }

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

      cloudBackupService.writeBackup(
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

      KeysetRepairState.RepairComplete(
        updatedKeybox = keyboxWithNewKeyset,
        signedKeysetVerification = signedKeysetVerification
      )
    }

  private fun markRepaired() {
    logInfo { "Marking keyset as repaired" }
    _syncStatus.value = SpendingKeysetSyncStatus.Synced
  }

  /**
   * Resolves each server keyset individually rather than assuming one source can supply them all.
   *
   * Recoverability is a per-keyset property:
   * - [LegacyRemoteKeyset] carries full extended descriptors in the listKeysets response, so it can
   *   be reconstructed directly with no descriptor backup and no hardware interaction.
   * - [PrivateMultisigRemoteKeyset] only carries bare public keys; it can be recovered from an
   *   unsealed descriptor backup, or reused from the local keybox if we already have it.
   * - A private keyset with neither is not recoverable from server state at all, and is omitted
   *   here so the caller can report it instead of silently repairing to an incomplete list.
   */
  private fun resolveKeysetsPerKeyset(
    account: FullAccount,
    response: ListKeysetsResponse,
    unsealedKeysets: List<SpendingKeyset>,
  ): List<SpendingKeyset> {
    val unsealedById = unsealedKeysets.associateBy { it.f8eSpendingKeyset.keysetId }
    val localById = account.keybox.keysets.associateBy { it.f8eSpendingKeyset.keysetId }

    return response.keysets.mapNotNull { remoteKeyset ->
      when (remoteKeyset) {
        is LegacyRemoteKeyset -> remoteKeyset.toSpendingKeyset(uuidGenerator)
        is PrivateMultisigRemoteKeyset ->
          unsealedById[remoteKeyset.keysetId] ?: localById[remoteKeyset.keysetId]
      }
    }
  }

  private fun determineRepairDataSource(
    account: FullAccount,
    response: ListKeysetsResponse,
  ): Result<RepairDataSource, KeysetRepairError> {
    val privateKeysets = response.keysets.filterIsInstance<PrivateMultisigRemoteKeyset>()
    val activePrivateKeysetId = account.keybox.activeSpendingKeyset
      .takeIf { it.isPrivateWallet }
      ?.f8eSpendingKeyset
      ?.keysetId

    if (privateKeysets.isEmpty()) return Ok(RepairDataSource.LegacyOnly)

    if (privateKeysets.size == 1 && privateKeysets.single().keysetId == activePrivateKeysetId) {
      return Ok(RepairDataSource.DirectFromResponse)
    }

    // Classify every private keyset by how (or whether) it can be resolved, rather than inferring
    // it from aggregate conditions. Earlier versions checked "are there any backups at all" and
    // "are any keysets missing locally" as separate branches, which let partially-covered accounts
    // through: the preflight would request a hardware tap, then the repair would abort on a keyset
    // the backups never covered.
    val localKeysetIds = account.keybox.keysets
      .map { it.f8eSpendingKeyset.keysetId }
      .toSet()
    // Backups are only usable if we also hold the SSEK needed to unseal them.
    val usableBackupKeysetIds = when (response.wrappedSsek) {
      null -> emptySet()
      else -> response.descriptorBackups.map { it.keysetId }.toSet()
    }

    val unresolvableKeysetIds = privateKeysets
      .map { it.keysetId }
      .filterNot { it in localKeysetIds || it in usableBackupKeysetIds }
      .toSet()

    // Terminal: these cannot be reconstructed from server state at all, because a private keyset's
    // chaincode never leaves the client. Reported before the flow starts so the customer is never
    // asked for a hardware tap that cannot lead anywhere.
    if (unresolvableKeysetIds.isNotEmpty()) {
      return Err(
        KeysetRepairError.UnresolvableKeysets(unresolvedKeysetIds = unresolvableKeysetIds)
      )
    }

    // Everything is resolvable. Only unseal if some keyset actually requires it; otherwise the local
    // keysets suffice and no hardware tap is needed.
    val requiresUnsealing = privateKeysets.any {
      it.keysetId !in localKeysetIds && it.keysetId in usableBackupKeysetIds
    }
    return when {
      requiresUnsealing -> Ok(RepairDataSource.DescriptorBackups)
      else -> Ok(RepairDataSource.LocalPrivateKeysets)
    }
  }

  private fun buildKeysetsDirectlyFromResponse(
    account: FullAccount,
    response: ListKeysetsResponse,
  ): Result<List<SpendingKeyset>, KeysetRepairError> =
    binding {
      val activePrivateKeyset = account.keybox.activeSpendingKeyset
        .takeIf { it.isPrivateWallet }
        ?: Err(
          KeysetRepairError.FetchKeysetsFailed(
            cause = IllegalStateException("Active private keyset not available locally")
          )
        ).bind()

      response.keysets.map { remoteKeyset ->
        when (remoteKeyset) {
          is LegacyRemoteKeyset -> remoteKeyset.toSpendingKeyset(uuidGenerator)
          is PrivateMultisigRemoteKeyset -> {
            if (remoteKeyset.keysetId != activePrivateKeyset.f8eSpendingKeyset.keysetId) {
              Err(
                KeysetRepairError.FetchKeysetsFailed(
                  cause = IllegalStateException(
                    "Cannot reconstruct private keyset ${remoteKeyset.keysetId} without descriptor backup"
                  )
                )
              ).bind()
            }

            activePrivateKeyset
          }
        }
      }
    }

  private suspend fun getCloudBackupSealedCsek(): SealedCsek? {
    val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .get() ?: return null

    val backup = cloudBackupService.readActiveBackup(cloudAccount)
      .get() ?: return null

    return backup.fullAccountFields?.sealedHwEncryptionKey
  }
}

private sealed interface RepairDataSource {
  data object LegacyOnly : RepairDataSource

  data object DirectFromResponse : RepairDataSource

  data object DescriptorBackups : RepairDataSource

  /**
   * Every private keyset the server knows about is already present in the local keybox, so the
   * local [SpendingKeyset]s can be reused directly. No descriptor backups and no SSEK unsealing
   * are required, which means no hardware tap either.
   */
  data object LocalPrivateKeysets : RepairDataSource
}

/**
 * Extension property to access fullAccountFields from CloudBackup
 */
private val CloudBackup.fullAccountFields
  get() = when (this) {
    is CloudBackupV2 -> fullAccountFields
    is CloudBackupV3 -> fullAccountFields
  }

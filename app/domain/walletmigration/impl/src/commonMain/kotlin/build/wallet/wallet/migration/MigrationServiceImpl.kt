package build.wallet.wallet.migration

import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.backup.DescriptorBackup
import bitkey.recovery.DescriptorBackupService
import bitkey.recovery.DescriptorBackupVerificationDao
import bitkey.recovery.VerifiedBackup
import build.wallet.account.AccountService
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthSignatureMismatch
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.isPrivateWallet
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.crypto.PublicKey
import build.wallet.database.sqldelight.PrivateWalletMigrationEntity
import build.wallet.database.sqldelight.W3UpgradeMigrationEntity
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.RotateAuthKeysF8eClient
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.money.BitcoinMoney
import build.wallet.notifications.DeviceTokenManager
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDao
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepService
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import build.wallet.relationships.DelegatedDecryptionKeyService
import build.wallet.relationships.EndorseTrustedContactsService
import build.wallet.relationships.RelationshipsService
import build.wallet.relationships.syncAndVerifyRelationships
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import io.ktor.http.HttpStatusCode.Companion.NotFound
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

@BitkeyInject(AppScope::class)
@Suppress("LargeClass")
class MigrationServiceImpl(
  private val appKeysGenerator: AppKeysGenerator,
  private val authF8eClient: AuthF8eClient,
  private val createKeysetClient: CreateAccountKeysetV2F8eClient,
  private val uuidGenerator: UuidGenerator,
  private val accountService: AccountService,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokensService: AuthTokensService,
  private val keyboxDao: KeyboxDao,
  private val privateWalletMigrationDao: PrivateWalletMigrationDao,
  private val w3UpgradeDao: W3UpgradeDao,
  private val w3UpgradeCheckpointWriter: W3UpgradeCheckpointWriter,
  private val ssekDao: SsekDao,
  private val onboardingKeyboxSealedSsekDao: OnboardingKeyboxSealedSsekDao,
  private val descriptorBackupService: DescriptorBackupService,
  private val descriptorBackupVerificationDao: DescriptorBackupVerificationDao,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  private val rotateAuthKeysF8eClient: RotateAuthKeysF8eClient,
  private val sweepService: SweepService,
  private val delegatedDecryptionKeyService: DelegatedDecryptionKeyService,
  private val relationshipsService: RelationshipsService,
  private val endorseTrustedContactsService: EndorseTrustedContactsService,
  private val deviceTokenManager: DeviceTokenManager,
) : MigrationService {
  override suspend fun resume(type: MigrationType): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val account = getActiveFullAccount().bind()

      when (type) {
        MigrationType.PrivateWalletMigration -> {
          val entity = privateWalletMigrationDao.currentState()
            .first()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()
          val keyset = entity?.toSpendingKeyset(account)

          when {
            // If the account already has a private wallet keyset and there is no persisted
            // migration state, there's nothing to migrate. We must still allow interrupted
            // migrations (entity != null) to resume even after the local keybox has already
            // been flipped to private, because descriptor backup, server activation, cloud
            // backup, and sweep may still be incomplete.
            entity == null &&
              account.keybox.activeSpendingKeyset.f8eSpendingKeyset.isPrivateWallet -> {
              logInfo { "Account already has a private wallet, no migration needed" }
              MigrationProgress.Completed(type)
            }
            entity?.newHardwareKey == null -> MigrationProgress.NotStarted(type)
            keyset == null -> MigrationProgress.NotStarted(type)
            !entity.descriptorBackupCompleted -> MigrationProgress.NotStarted(type)
            !entity.serverKeysetActivated -> MigrationProgress.NotStarted(type)
            !entity.cloudBackupCompleted -> MigrationProgress.CloudBackup(
              type,
              account.keybox,
              keyset
            )
            !entity.sweepCompleted -> MigrationProgress.LocalKeyboxActivation(
              type,
              account.keybox,
              keyset
            )
            else -> MigrationProgress.Completed(type)
          }
        }
        MigrationType.W3Upgrade -> {
          val entity = w3UpgradeDao.currentState()
            .first()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()
          val keyset = entity?.toSpendingKeyset(account)

          when {
            // If the account already has W3 hardware and there is no real migration
            // checkpoint (newHardwareKey), the upgrade already completed. This covers
            // both cleared rows (entity == null) and stale placeholder rows left by
            // a previous cloud restore (resumedFromCloudBackup without newHardwareKey).
            (entity == null || entity.newHardwareKey == null) &&
              account.keybox.config.hardwareType == HardwareType.W3 ->
              MigrationProgress.Completed(type)
            entity?.resumedFromCloudBackup == true && entity.newHardwareKey == null ->
              MigrationProgress.NotStarted(type, resumedFromCloudBackup = true)
            entity?.newHardwareKey == null -> MigrationProgress.NotStarted(type)
            keyset == null -> MigrationProgress.NotStarted(type)
            !entity.authKeyRotationCompleted -> {
              val authKeyRotation = MigrationProgress.AuthKeyRotation(
                type,
                account.keybox,
                keyset,
                resumedFromCloudBackup = entity.resumedFromCloudBackup,
                sealedSsekForDecryption = entity.sealedSsekForDecryption
              )
              // If pending auth rotation data was persisted (crash recovery),
              // pre-populate the key material so the flow can resume without
              // re-tapping hardware.  We intentionally leave proof null — it is
              // only required when the server call still needs to happen, and
              // proceed() will require the caller to supply a real proof in that
              // case.
              val pendingGlobalKey = entity.pendingAppGlobalAuthKey
              val pendingRecoveryKey = entity.pendingAppRecoveryAuthKey
              val pendingHwSignature = entity.pendingAppGlobalAuthKeyHwSignature
              val pendingHwAuth = entity.pendingHwAuthPublicKey
              val pendingSignedId = entity.pendingHwSignedAccountId
              if (
                pendingGlobalKey != null &&
                pendingRecoveryKey != null &&
                pendingHwSignature != null
              ) {
                if (pendingHwAuth != null && pendingSignedId != null) {
                  authKeyRotation.copy(
                    newAppAuthKeys = AppAuthPublicKeys(
                      appGlobalAuthPublicKey = pendingGlobalKey,
                      appRecoveryAuthPublicKey = pendingRecoveryKey,
                      appGlobalAuthKeyHwSignature = pendingHwSignature
                    ),
                    hwAuthPublicKey = pendingHwAuth,
                    hwSignedAccountId = pendingSignedId
                  )
                } else {
                  authKeyRotation
                }
              } else {
                authKeyRotation
              }
            }
            !entity.descriptorBackupCompleted ->
              MigrationProgress.DescriptorBackup(
                type,
                account.keybox,
                keyset,
                resumedFromCloudBackup = entity.resumedFromCloudBackup,
                sealedSsekForDecryption = entity.sealedSsekForDecryption
              )
            !entity.serverKeysetActivated ->
              MigrationProgress.ServerKeysetActivation(type, account.keybox, keyset)
            !entity.ddkBackedUp -> MigrationProgress.DdkBackup(type, account.keybox, keyset)
            !entity.cloudBackupCompleted -> MigrationProgress.CloudBackup(
              type,
              account.keybox,
              keyset
            )
            !entity.sweepCompleted -> MigrationProgress.LocalKeyboxActivation(
              type,
              account.keybox,
              keyset
            )
            else -> MigrationProgress.Completed(type)
          }
        }
      }
    }
  }

  private fun PrivateWalletMigrationEntity.toSpendingKeyset(
    account: FullAccount,
  ): SpendingKeyset? {
    val hwKey = newHardwareKey ?: return null
    val appKey = newAppKey ?: return null
    val serverKey = newServerKey ?: return null
    val localId = keysetLocalId ?: return null
    return SpendingKeyset(
      localId = localId,
      networkType = account.keybox.config.bitcoinNetworkType,
      appKey = appKey,
      hardwareKey = hwKey,
      f8eSpendingKeyset = serverKey
    )
  }

  private fun W3UpgradeMigrationEntity.toSpendingKeyset(account: FullAccount): SpendingKeyset? {
    val hwKey = newHardwareKey ?: return null
    val appKey = newAppKey ?: return null
    val serverKey = newServerKey ?: return null
    val localId = keysetLocalId ?: return null
    return SpendingKeyset(
      localId = localId,
      networkType = account.keybox.config.bitcoinNetworkType,
      appKey = appKey,
      hardwareKey = hwKey,
      f8eSpendingKeyset = serverKey
    )
  }

  // region State Mutation Helpers

  private suspend fun saveHardwareKey(
    type: MigrationType,
    hwKey: HwSpendingPublicKey,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.saveHardwareKey(hwKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.saveHardwareKey(hwKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun saveAppKey(
    type: MigrationType,
    appKey: AppSpendingPublicKey,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.saveAppKey(appKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.saveAppKey(appKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun saveServerKey(
    type: MigrationType,
    serverKey: F8eSpendingKeyset,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.saveServerKey(serverKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.saveServerKey(serverKey)
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun saveKeysetLocalId(
    type: MigrationType,
    localId: String,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.saveKeysetLocalId(localId)
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.saveKeysetLocalId(localId)
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setDescriptorBackupComplete(
    type: MigrationType,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.setDescriptorBackupComplete()
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setDescriptorBackupComplete()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setServerKeysetActive(type: MigrationType): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.setServerKeysetActive()
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setServerKeysetActive()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setCloudBackupComplete(type: MigrationType): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.setCloudBackupComplete()
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setCloudBackupComplete()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setSweepCompleted(type: MigrationType): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration ->
        privateWalletMigrationDao.setSweepCompleted()
          .mapError { MigrationError.StatePersistenceFailed(it) }
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setSweepCompleted()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setAuthKeyRotationComplete(
    type: MigrationType,
  ): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration -> Ok(Unit)
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setAuthKeyRotationComplete()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  private suspend fun setDdkBackedUp(type: MigrationType): Result<Unit, MigrationError> {
    return when (type) {
      MigrationType.PrivateWalletMigration -> Ok(Unit)
      MigrationType.W3Upgrade ->
        w3UpgradeDao.setDdkBackedUp()
          .mapError { MigrationError.StatePersistenceFailed(it) }
    }
  }

  // endregion

  override suspend fun proceed(
    state: MigrationProgress,
  ): Result<MigrationProgress, MigrationError> {
    return when (state) {
      is MigrationProgress.NotStarted -> handleNotStarted(state)
      is MigrationProgress.CreateNewKeyset -> handleCreateNewKeyset(state)
      is MigrationProgress.AuthKeyRotation -> handleAuthKeyRotation(state)
      is MigrationProgress.DescriptorBackup -> handleDescriptorBackup(state)
      is MigrationProgress.ServerKeysetActivation -> handleServerKeysetActivation(state)
      is MigrationProgress.HardwareDescriptorProvisioning -> handleHardwareDescriptorProvisioning(
        state
      )
      is MigrationProgress.DdkBackup -> handleDdkBackup(state)
      is MigrationProgress.CloudBackup -> handleCloudBackup(state)
      is MigrationProgress.StartDelayNotify -> handleStartDelayNotify(state)
      is MigrationProgress.Sweep -> handleSweep(state)
      is MigrationProgress.LocalKeyboxActivation -> handleLocalKeyboxActivation(state)
      is MigrationProgress.Completed -> Ok(state)
    }
  }

  private suspend fun getActiveFullAccount(): Result<FullAccount, MigrationError> {
    val account = accountService.activeAccount().firstOrNull()
    return when (account) {
      is FullAccount -> Ok(account)
      else -> Err(MigrationError.KeyboxNotFound())
    }
  }

  private suspend fun handleNotStarted(
    @Suppress("UnusedParameter")
    state: MigrationProgress.NotStarted,
  ): Result<MigrationProgress, MigrationError> {
    // NotStarted state requires the caller to call state.start() with the required
    // parameters (keybox, hwProofOfPossession, sealedSsek) before calling proceed().
    // If proceed() is called directly on NotStarted, it means the caller hasn't
    // initiated the migration properly.
    return Err(
      MigrationError.InvalidState(
        "Cannot proceed from NotStarted. Call state.next() with required parameters first."
      )
    )
  }

  @Suppress("CyclomaticComplexMethod")
  private suspend fun handleCreateNewKeyset(
    state: MigrationProgress.CreateNewKeyset,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val account = getActiveFullAccount().bind()
      val existingHwKey: HwSpendingPublicKey?
      val existingAppKey: AppSpendingPublicKey?
      val existingServerKey: F8eSpendingKeyset?
      val existingKeysetLocalId: String?
      when (state.type) {
        MigrationType.PrivateWalletMigration -> {
          val entity = privateWalletMigrationDao.currentState()
            .first()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()
          existingHwKey = entity?.newHardwareKey
          existingAppKey = entity?.newAppKey
          existingServerKey = entity?.newServerKey
          existingKeysetLocalId = entity?.keysetLocalId
        }
        MigrationType.W3Upgrade -> {
          val entity = w3UpgradeDao.currentState()
            .first()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()
          existingHwKey = entity?.newHardwareKey
          existingAppKey = entity?.newAppKey
          existingServerKey = entity?.newServerKey
          existingKeysetLocalId = entity?.keysetLocalId
        }
      }

      // Get hardware key - prefer saved state for resumption, otherwise use the one passed in
      val hwSpendingKey = existingHwKey ?: state.newHwSpendingKey.also {
        if (state is MigrationProgress.CreateNewKeyset.PrivateWalletMigration) {
          // Private-wallet migrations still persist this step incrementally until the shared
          // create-keyset checkpoint is revisited there as well.
          saveHardwareKey(state.type, it).bind()
          logInfo { "Saved new hardware key" }
        }
      }

      // Generate new app spending key if not already saved
      val appKeyBundle = if (existingAppKey != null) {
        logInfo { "Resuming with existing app key" }
        null
      } else {
        appKeysGenerator.generateKeyBundle()
          .mapError { MigrationError.AppKeyGenerationFailed(it) }
          .bind()
          .also { bundle ->
            if (state is MigrationProgress.CreateNewKeyset.PrivateWalletMigration) {
              saveAppKey(state.type, bundle.spendingKey).bind()
              logInfo { "Generated and saved new app spending key" }
            }
          }
      }

      val appSpendingKey = existingAppKey ?: appKeyBundle!!.spendingKey

      // Create server keyset if not already saved
      val (serverKeyset, keysetLocalId) = if (existingServerKey != null && existingKeysetLocalId != null) {
        logInfo { "Resuming with existing server keyset" }
        existingServerKey to existingKeysetLocalId
      } else {
        val serverKeyset = createKeysetClient.createKeyset(
          f8eEnvironment = account.keybox.config.f8eEnvironment,
          fullAccountId = account.accountId,
          hardwareSpendingKey = hwSpendingKey,
          appSpendingKey = appSpendingKey,
          network = account.keybox.config.bitcoinNetworkType,
          appAuthKey = account.keybox.activeAppKeyBundle.authKey
        )
          .mapError { MigrationError.ServerKeysetCreationFailed(it) }
          .bind()

        logInfo { "Created server keyset: ${serverKeyset.keysetId}" }

        val localId = uuidGenerator.random()

        if (state is MigrationProgress.CreateNewKeyset.PrivateWalletMigration) {
          saveServerKey(state.type, serverKeyset).bind()
          saveKeysetLocalId(state.type, localId).bind()
        }

        serverKeyset to localId
      }

      val newKeyset = SpendingKeyset(
        localId = keysetLocalId,
        networkType = account.keybox.config.bitcoinNetworkType,
        appKey = appSpendingKey,
        hardwareKey = hwSpendingKey,
        f8eSpendingKeyset = serverKeyset
      )

      // Save the keybox with the new keyset as active, matching the original
      // PrivateWalletMigrationServiceImpl.createServerKeyAndActivate behavior.
      // For W3 upgrades, also update the config to reflect the new hardware type.
      val updatedKeybox = account.keybox.withNewSpendingKeyset(newKeyset).let { keybox ->
        when (state.type) {
          MigrationType.W3Upgrade -> keybox.copy(
            config = keybox.config.copy(hardwareType = HardwareType.W3)
          )
          else -> keybox
        }
      }

      // When a W3 upgrade is resumed after cloud recovery, the local keybox may be missing
      // historical keysets that existed before the cloud backup. Fetch keyset metadata from
      // F8e to identify any gaps, and retrieve the server-held wrapped SSEK needed to decrypt
      // those historical descriptor backups during the upcoming descriptor backup step.
      val sealedSsekForDecryption = if (state is MigrationProgress.CreateNewKeyset.W3Upgrade &&
        state.resumedFromCloudBackup
      ) {
        val listKeysetsResponse = listKeysetsF8eClient.listKeysets(
          f8eEnvironment = account.keybox.config.f8eEnvironment,
          fullAccountId = account.accountId
        )
          .mapError { MigrationError.DescriptorBackupFailed(it) }
          .bind()
        val missingHistoricalKeysetIds = missingHistoricalKeysetIds(
          descriptorBackups = listKeysetsResponse.descriptorBackups,
          localKeysets = account.keybox.keysets
        )

        if (missingHistoricalKeysetIds.isEmpty()) {
          null
        } else {
          ensureNotNull(listKeysetsResponse.wrappedSsek) {
            MigrationError.MissingContext.Generic(
              "Resumed W3 upgrade requires wrappedSsek to decrypt historical descriptor backups."
            )
          }
        }
      } else {
        null
      }

      when (state) {
        is MigrationProgress.CreateNewKeyset.PrivateWalletMigration -> {
          keyboxDao.saveKeyboxAsActive(updatedKeybox)
            .mapError { MigrationError.LocalKeyboxActivationFailed(it) }
            .bind()
        }
        is MigrationProgress.CreateNewKeyset.W3Upgrade -> {
          onboardingKeyboxSealedSsekDao.set(state.sealedSsek)
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()

          w3UpgradeCheckpointWriter.persistCreateNewKeysetCheckpoint(
            oldDeviceSerial = state.oldDeviceSerial,
            oldHardwareFingerprint = state.oldHardwareFingerprint,
            newDeviceSerial = state.newDeviceSerial,
            newKeyset = newKeyset,
            updatedKeybox = updatedKeybox,
            sealedSsekForDecryption = sealedSsekForDecryption
          )
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .onFailure {
              onboardingKeyboxSealedSsekDao.clear()
                .logFailure { "Failed to clear sealed SSEK after W3 checkpoint write failure" }
            }
            .bind()
        }
      }

      logInfo { "Created new keyset: ${newKeyset.localId}" }

      state.next(
        newKeyset = newKeyset,
        updatedKeybox = updatedKeybox,
        sealedSsekForDecryption = sealedSsekForDecryption
      )
    }
  }

  private suspend fun handleDescriptorBackup(
    state: MigrationProgress.DescriptorBackup,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val account = getActiveFullAccount().bind()

      state.ssek?.let { ssek ->
        val sealedSsek = ensureNotNull(state.sealedSsek) {
          MigrationError.MissingContext.Generic("Descriptor backup requires sealedSsek when ssek is present.")
        }
        ssekDao.set(sealedSsek, ssek)
          .mapError { MigrationError.DescriptorBackupFailed(it) }
          .bind()
      }

      val sealedSsekForEncryption = when (state.type) {
        MigrationType.W3Upgrade -> {
          state.sealedSsek ?: onboardingKeyboxSealedSsekDao.get()
            .mapError { MigrationError.DescriptorBackupFailed(it) }
            .bind()
            ?: Err(
              MigrationError.MissingContext.Generic(
                "Sealed SSEK unavailable for W3 descriptor backup"
              )
            ).bind()
        }
        MigrationType.PrivateWalletMigration ->
          ensureNotNull(state.sealedSsek) {
            MigrationError.MissingContext.Generic("Descriptor backup requires sealedSsek.")
          }
      }

      val listKeysetsResponse = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      )
        .mapError { MigrationError.DescriptorBackupFailed(it) }
        .bind()

      val keysetsToEncrypt: List<SpendingKeyset>
      val descriptorsToDecrypt: List<DescriptorBackup>
      val sealedSsekForDecryption: SealedSsek?

      // For W3 upgrade, use the "EncryptOnly" pattern: re-encrypt all keysets from scratch
      // with the new SSEK
      if (state.type == MigrationType.W3Upgrade) {
        // Get ALL keysets to re-encrypt: local keybox keysets (if authoritative) or F8e keysets
        val allKeysets = if (state.currentKeybox.canUseKeyboxKeysets) {
          state.currentKeybox.keysets
        } else {
          listKeysetsResponse.keysets
            .filterIsInstance<LegacyRemoteKeyset>()
            .toSpendingKeysets(uuidGenerator)
        }
        if (state.resumedFromCloudBackup) {
          // Resumed-from-cloud W3 upgrades may have historical keysets that were recovered
          // from F8e but are absent from the local keybox. Those descriptor backups must be
          // decrypted (not re-encrypted) using the server-held wrapped SSEK, while all other
          // keysets are re-encrypted with the new SSEK. The SSEK fallback priority is:
          //   1. Already carried in the migration state (from the CreateNewKeyset step)
          //   2. Persisted in the W3 upgrade DAO (from a prior interrupted attempt)
          //   3. The current encryption SSEK (from onboarding/pairing)
          val missingHistoricalKeysetIds = missingHistoricalKeysetIds(
            descriptorBackups = listKeysetsResponse.descriptorBackups,
            localKeysets = state.currentKeybox.keysets
          )

          descriptorsToDecrypt = listKeysetsResponse.descriptorBackups
            .filter { it.keysetId in missingHistoricalKeysetIds }
          keysetsToEncrypt = (allKeysets + state.newKeyset)
            .filter { it.f8eSpendingKeyset.keysetId !in missingHistoricalKeysetIds }
            .distinctBy { it.f8eSpendingKeyset.keysetId }
          sealedSsekForDecryption = when {
            descriptorsToDecrypt.isEmpty() -> null
            state.sealedSsekForDecryption != null -> state.sealedSsekForDecryption
            else -> {
              val w3Entity = w3UpgradeDao.currentState()
                .first()
                .mapError { MigrationError.StatePersistenceFailed(it) }
                .bind()
              w3Entity?.sealedSsekForDecryption ?: listKeysetsResponse.wrappedSsek
            }
          }
        } else {
          // Include the new keyset being created as part of the upgrade
          keysetsToEncrypt = (allKeysets + state.newKeyset)
            .distinctBy { it.f8eSpendingKeyset.keysetId }
          // No decryption needed — we're re-encrypting everything from scratch
          descriptorsToDecrypt = emptyList()
          sealedSsekForDecryption = null
        }
      } else {
        val serverKeysets = listKeysetsResponse.keysets
          .filterIsInstance<LegacyRemoteKeyset>()
          .toSpendingKeysets(uuidGenerator)
        val existingDescriptorBackups = listKeysetsResponse.descriptorBackups
        val existingBackupKeysetIds = existingDescriptorBackups.map { it.keysetId }.toSet()
        keysetsToEncrypt = (serverKeysets + state.newKeyset)
          .filter { it.f8eSpendingKeyset.keysetId !in existingBackupKeysetIds }
        descriptorsToDecrypt = existingDescriptorBackups
        sealedSsekForDecryption = listKeysetsResponse.wrappedSsek
      }
      val proof = when (state.type) {
        MigrationType.W3Upgrade ->
          ensureNotNull(state.proof) {
            MigrationError.MissingContext.Generic("Descriptor backup requires W3 action proof.")
          }
        MigrationType.PrivateWalletMigration ->
          ensureNotNull(state.hwProofOfPossession?.let(PrivilegedActionProof::HwKeyProof)) {
            MigrationError.MissingContext.Generic("Descriptor backup requires hardware proof of possession.")
          }
      }

      val uploadedKeysets = descriptorBackupService.uploadDescriptorBackups(
        accountId = account.accountId,
        sealedSsekForDecryption = sealedSsekForDecryption,
        sealedSsekForEncryption = sealedSsekForEncryption,
        appAuthKey = state.currentKeybox.activeAppKeyBundle.authKey,
        proof = proof,
        descriptorsToDecrypt = descriptorsToDecrypt,
        keysetsToEncrypt = keysetsToEncrypt
      )
        .mapError { MigrationError.DescriptorBackupFailed(it) }
        .bind()

      if (state.type == MigrationType.W3Upgrade) {
        descriptorBackupVerificationDao.replaceAllVerifiedBackups(
          uploadedKeysets.map { VerifiedBackup(keysetId = it.f8eSpendingKeyset.keysetId) }
        )
          .mapError { MigrationError.DescriptorBackupFailed(it) }
          .bind()
      }

      val nextKeybox = if (state.type == MigrationType.W3Upgrade && state.resumedFromCloudBackup) {
        repairKeyboxAfterCloudResume(state, uploadedKeysets).bind()
      } else {
        state.currentKeybox
      }

      setDescriptorBackupComplete(state.type).bind()

      logInfo {
        "Descriptor backups uploaded for ${descriptorsToDecrypt.size + keysetsToEncrypt.size} keysets"
      }

      state.next(currentKeybox = nextKeybox)
    }
  }

  private suspend fun repairKeyboxAfterCloudResume(
    state: MigrationProgress.DescriptorBackup,
    uploadedKeysets: List<SpendingKeyset>,
  ): Result<Keybox, MigrationError> {
    return coroutineBinding {
      val repairedKeybox = state.currentKeybox.copy(
        activeSpendingKeyset = state.newKeyset,
        keysets = uploadedKeysets.distinctBy { it.f8eSpendingKeyset.keysetId },
        canUseKeyboxKeysets = true
      )
      keyboxDao.saveKeyboxAsActive(repairedKeybox)
        .mapError { MigrationError.LocalKeyboxActivationFailed(it) }
        .bind()
      val persistedSealedSsekForDecryption = w3UpgradeDao.currentState()
        .first()
        .mapError { MigrationError.StatePersistenceFailed(it) }
        .bind()
        ?.sealedSsekForDecryption
      if (state.sealedSsekForDecryption != null || persistedSealedSsekForDecryption != null) {
        w3UpgradeDao.setSealedSsekForDecryption(null)
          .mapError { MigrationError.StatePersistenceFailed(it) }
          .bind()
      }
      repairedKeybox
    }
  }

  private suspend fun handleServerKeysetActivation(
    state: MigrationProgress.ServerKeysetActivation,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val account = getActiveFullAccount().bind()
      val proof = when (state.type) {
        MigrationType.W3Upgrade ->
          ensureNotNull(state.proof) {
            MigrationError.MissingContext.Generic("Keyset activation requires W3 action proof.")
          }
        MigrationType.PrivateWalletMigration ->
          ensureNotNull(state.hwProofOfPossession?.let(PrivilegedActionProof::HwKeyProof)) {
            MigrationError.MissingContext.Generic("Keyset activation requires hardware proof of possession.")
          }
      }

      val signedKeysResponse = setActiveSpendingKeysetF8eClient.set(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        keysetId = state.newKeyset.f8eSpendingKeyset.keysetId,
        appAuthKey = state.currentKeybox.activeAppKeyBundle.authKey,
        proof = proof
      )
        .mapError { MigrationError.ServerKeysetActivationFailed(it) }
        .bind()

      return@coroutineBinding when (state.type) {
        MigrationType.W3Upgrade -> {
          logInfo { "Server keyset activated; awaiting hardware descriptor provisioning" }

          state.next(
            signedKeysResponse = ensureNotNull(signedKeysResponse) {
              MigrationError.MissingContext.Generic("W3 upgrade keyset activation requires signed keyset verification data")
            }
          )
        }
        MigrationType.PrivateWalletMigration -> {
          setServerKeysetActive(state.type).bind()
          logInfo { "Server keyset activated: ${state.newKeyset.f8eSpendingKeyset.keysetId}" }
          state.next()
        }
      }
    }
  }

  private data class AuthRotationKeys(
    val newAppAuthKeys: AppAuthPublicKeys,
    val hwAuthPublicKey: HwAuthPublicKey,
    val oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    val oldHwAuthPublicKey: HwAuthPublicKey,
  )

  /**
   * Attempts to reconstruct [AuthRotationKeys] from previously persisted pending
   * auth rotation data in the DAO.  Returns `null` if any required field is missing,
   * which means no prior rotation attempt was persisted.
   */
  private fun recoverPersistedAuthKeys(entity: W3UpgradeMigrationEntity?): AuthRotationKeys? {
    val global = entity?.pendingAppGlobalAuthKey ?: return null
    val recovery = entity.pendingAppRecoveryAuthKey ?: return null
    val hwSig = entity.pendingAppGlobalAuthKeyHwSignature ?: return null
    val hwAuth = entity.pendingHwAuthPublicKey ?: return null
    val oldAppGlobal = entity.preRotationAppGlobalAuthKey ?: return null
    val oldHwAuth = entity.preRotationHwAuthPublicKey ?: return null
    return AuthRotationKeys(
      newAppAuthKeys = AppAuthPublicKeys(
        appGlobalAuthPublicKey = global,
        appRecoveryAuthPublicKey = recovery,
        appGlobalAuthKeyHwSignature = hwSig
      ),
      hwAuthPublicKey = hwAuth,
      oldAppGlobalAuthKey = oldAppGlobal,
      oldHwAuthPublicKey = oldHwAuth
    )
  }

  /**
   * Detects whether the server already accepted the pending auth rotation.
   *
   * For the normal W3 upgrade the meaningful signal is whether the pending new hardware auth key
   * can start authentication on the server. For resumed-from-cloud-backup, the hardware auth key
   * stays unchanged, so the meaningful signal is whether the pending new recovery auth key can
   * start recovery-scope authentication on the server.
   */
  private suspend fun isAuthRotationAlreadyApplied(
    account: FullAccount,
    keys: AuthRotationKeys,
    resumedFromCloudBackup: Boolean,
  ): Result<Boolean, MigrationError> {
    val authResult = if (resumedFromCloudBackup) {
      authF8eClient.initiateAuthentication(
        f8eEnvironment = account.config.f8eEnvironment,
        authPublicKey = keys.newAppAuthKeys.appRecoveryAuthPublicKey,
        tokenScope = Recovery
      )
    } else {
      authF8eClient.initiateAuthentication(
        f8eEnvironment = account.config.f8eEnvironment,
        authPublicKey = keys.hwAuthPublicKey
      )
    }
    return if (authResult.isOk) {
      logInfo {
        if (resumedFromCloudBackup) {
          "Rotated recovery auth key is already active on server"
        } else {
          "Rotated hardware auth key is already active on server"
        }
      }
      Ok(true)
    } else {
      when (val error = authResult.error) {
        is HttpError.ClientError ->
          if (error.response.status == NotFound) {
            Ok(false)
          } else {
            Err(MigrationError.AuthKeyRotationFailed(error))
          }
        else -> Err(MigrationError.AuthKeyRotationFailed(error))
      }
    }
  }

  /**
   * Performs the server-side auth key rotation or recovers persisted keys if the
   * server rotation already completed in a previous attempt.
   *
   * When the checkpoint flag (`serverAuthRotationCompleted`) was NOT written but
   * persisted pending keys exist from a prior attempt, those keys are verified
   * against the server first.  If they are already active the server accepted
   * them before the crash and we reuse them; otherwise we fall through to a
   * normal rotation with the caller-supplied keys.
   */
  private suspend fun resolveServerAuthRotation(
    account: FullAccount,
    state: MigrationProgress.AuthKeyRotation,
    entity: W3UpgradeMigrationEntity?,
  ): Result<AuthRotationKeys, MigrationError> =
    coroutineBinding {
      val serverAlreadyRotated = entity?.serverAuthRotationCompleted == true

      if (!serverAlreadyRotated) {
        // Before overwriting persisted keys, check whether a prior attempt's
        // keys were already accepted by the server (crash-before-checkpoint).
        val priorKeys = recoverPersistedAuthKeys(entity)
        if (priorKeys != null) {
          val rotationAlreadyApplied = isAuthRotationAlreadyApplied(
            account = account,
            keys = priorKeys,
            resumedFromCloudBackup = state.resumedFromCloudBackup
          )
            .bind()
          when (rotationAlreadyApplied) {
            true -> {
              logInfo {
                "Previously persisted auth rotation is already active on server (crash recovery)"
              }
              w3UpgradeDao.setServerAuthRotationCompleted()
                .mapError { MigrationError.StatePersistenceFailed(it) }
                .bind()
              return@coroutineBinding priorKeys
            }
            false ->
              logInfo {
                "Persisted auth rotation is not yet active on server, proceeding with rotation"
              }
          }
        }

        // Proof is only required when we actually need to call the server.
        val proof = ensureNotNull(state.proof) {
          if (state.resumedFromCloudBackup) {
            MigrationError.MissingContext.W3AuthRotationNewHardwareActionProof
          } else {
            MigrationError.MissingContext.W3AuthRotationOldHardwareProof
          }
        }
        when {
          state.resumedFromCloudBackup && proof !is PrivilegedActionProof.HwSignedAction ->
            Err(
              MigrationError.MissingContext.Generic(
                "Resumed W3 auth rotation requires HwSignedAction proof type"
              )
            ).bind<Unit>()
          !state.resumedFromCloudBackup && proof !is PrivilegedActionProof.HwKeyProof ->
            Err(
              MigrationError.MissingContext.Generic(
                "W3 upgrade auth rotation requires HwKeyProof proof type"
              )
            ).bind<Unit>()
        }

        val newAppAuthKeys = ensureNotNull(state.newAppAuthKeys) {
          MigrationError.MissingContext.Generic("Auth key rotation requires newAppAuthKeys.")
        }
        val hwAuthPublicKey = ensureNotNull(state.hwAuthPublicKey) {
          MigrationError.MissingContext.Generic("Auth key rotation requires hwAuthPublicKey.")
        }
        val hwSignedAccountId = ensureNotNull(state.hwSignedAccountId) {
          MigrationError.MissingContext.Generic(
            "Auth key rotation requires signed rotation data. Populate via withRotationData() first."
          )
        }

        // Capture the pre-rotation keys before anything changes.
        // These are persisted so that post-rotation steps (TC endorsement
        // regeneration) can use the correct "old" keys on retry, even when
        // the local keybox has already been rotated by a previous attempt.
        val oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey
        val oldHwAuthPublicKey = account.keybox.activeHwKeyBundle.authKey

        // Persist the in-flight auth rotation data BEFORE calling the server.
        // This ensures we can reconstruct the auth rotation state on resume if
        // the app crashes after the server accepts but before local completion.
        w3UpgradeDao.savePendingAuthRotationData(
          newAppAuthKeys = newAppAuthKeys,
          hwAuthPublicKey = hwAuthPublicKey,
          hwSignedAccountId = hwSignedAccountId,
          oldAppGlobalAuthKey = oldAppGlobalAuthKey,
          oldHwAuthPublicKey = oldHwAuthPublicKey
        )
          .mapError { MigrationError.StatePersistenceFailed(it) }
          .bind()

        // Attempt the server rotation. Do NOT .bind() — if the app
        // previously crashed after the server accepted but before the
        // local checkpoint was written, a retry will fail because the
        // old key is stale.  We validate the result below instead.
        val serverRotateResult = rotateAuthKeysF8eClient.rotateKeyset(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId,
          oldAppAuthPublicKey = account.keybox.activeAppKeyBundle.authKey,
          newAppAuthPublicKeys = newAppAuthKeys,
          hwAuthPublicKey = hwAuthPublicKey,
          hwSignedAccountId = hwSignedAccountId,
          proof = proof,
          hardwareType = HardwareType.W3
        )

        if (serverRotateResult.isOk) {
          logInfo { "Auth keys rotated on server" }
        } else {
          // Server call failed.  Check whether the new keys are already
          // active (crash-before-checkpoint scenario).  If they are, the
          // rotation already succeeded in a prior attempt and we can
          // proceed.  If they're not, the rotation genuinely failed.
          logInfo {
            "Server rotate call failed, validating whether the pending rotation is already active"
          }
          val rotationAlreadyApplied = isAuthRotationAlreadyApplied(
            account = account,
            keys = AuthRotationKeys(
              newAppAuthKeys = newAppAuthKeys,
              hwAuthPublicKey = hwAuthPublicKey,
              oldAppGlobalAuthKey = oldAppGlobalAuthKey,
              oldHwAuthPublicKey = oldHwAuthPublicKey
            ),
            resumedFromCloudBackup = state.resumedFromCloudBackup
          )
            .bind()

          if (!rotationAlreadyApplied) {
            // Genuine server failure — propagate the original error.
            serverRotateResult
              .mapError { MigrationError.AuthKeyRotationFailed(it) }
              .bind()
          }
          logInfo { "Pending auth rotation is already active on server (crash recovery)" }
        }

        // Checkpoint: server rotation succeeded. On resume we skip the server call.
        w3UpgradeDao.setServerAuthRotationCompleted()
          .mapError { MigrationError.StatePersistenceFailed(it) }
          .bind()

        AuthRotationKeys(
          newAppAuthKeys = newAppAuthKeys,
          hwAuthPublicKey = hwAuthPublicKey,
          oldAppGlobalAuthKey = oldAppGlobalAuthKey,
          oldHwAuthPublicKey = oldHwAuthPublicKey
        )
      } else {
        // Server rotation already succeeded in a previous attempt. Use the
        // persisted keys — they are what the server actually accepted.  The
        // caller (UI) may have regenerated fresh keys on resume, so we must
        // ignore state.newAppAuthKeys to avoid server/local divergence.
        ensureNotNull(recoverPersistedAuthKeys(entity)) {
          MigrationError.MissingContext.Generic(
            "Server rotation completed but persisted auth rotation keys are missing"
          )
        }
      }
    }

  private suspend fun handleAuthKeyRotation(
    state: MigrationProgress.AuthKeyRotation,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      // Auth key rotation logic - implementation depends on the type
      when (state.type) {
        MigrationType.PrivateWalletMigration -> {
          logInfo { "Auth key rotation not required for ${state.type}" }
          return@coroutineBinding state.next(currentKeybox = state.currentKeybox)
        }
        MigrationType.W3Upgrade -> {
          val account = getActiveFullAccount().bind()

          // Check if server rotation already completed (crash recovery).
          // If the DAO has the serverAuthRotationCompleted flag, skip the server call
          // and use the persisted keys (which are what the server actually accepted).
          val entity = w3UpgradeDao.currentState()
            .first()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()

          val keys = resolveServerAuthRotation(account, state, entity).bind()

          val updatedKeybox = keyboxDao.rotateKeyboxAuthKeys(
            keyboxToRotate = account.keybox,
            appAuthKeys = keys.newAppAuthKeys,
            newHwAuthPublicKey = keys.hwAuthPublicKey
          )
            .mapError { MigrationError.AuthKeyRotationFailed(it) }
            .bind()

          // During the W3 upgrade the recovery app auth key rotates, and resumed W3 action
          // proofs still bind to Global-scope bearer tokens. Refresh both scopes so later
          // privileged actions can rely on consistent post-rotation auth state.
          rotateAppAuthTokens(
            account = account,
            newAppAuthKeys = keys.newAppAuthKeys
          )
            .mapError { MigrationError.AuthKeyRotationFailed(it) }
            .bind()

          logInfo { "Global and recovery auth tokens refreshed for rotated auth keys" }

          // Re-register the device token for push notifications. Auth key rotation
          // clears all device tokens on the server, so we need to proactively
          // re-register to avoid missing notifications (e.g. deposit while backgrounded).
          deviceTokenManager
            .addDeviceTokenIfPresentForAccount(account.accountId, Global)
            .result
            .logFailure { "Failed to re-register device token after auth key rotation" }

          // Regenerate trusted-contact key certificates that were bound to the
          // old auth keys, matching the dedicated auth-key rotation flow.
          // We use the persisted pre-rotation keys (not account.keybox) because
          // the local keybox may already have been rotated by a previous attempt
          // if we're retrying after a partial failure.
          //
          // Skip if already completed in a prior attempt — re-running with
          // pre-rotation "old" keys after certificates were already regenerated
          // would cause verification failures and mark contacts as TAMPERED.
          val tcAlreadyRegenerated = entity?.tcEndorsementsRegenerated == true
          if (!tcAlreadyRegenerated) {
            val rotatedAccount = account.copy(keybox = updatedKeybox)
            regenerateAndVerifyTrustedContactEndorsements(
              rotatedAccount = rotatedAccount,
              oldAppGlobalAuthKey = keys.oldAppGlobalAuthKey,
              oldHwAuthPublicKey = keys.oldHwAuthPublicKey,
              newHwAuthPublicKey = keys.hwAuthPublicKey,
              newAppAuthKeys = keys.newAppAuthKeys
            )
              .mapError { MigrationError.AuthKeyRotationFailed(it) }
              .bind()

            w3UpgradeDao.setTcEndorsementsRegenerated()
              .mapError { MigrationError.StatePersistenceFailed(it) }
              .bind()

            logInfo { "Trusted contact endorsements regenerated for rotated auth keys" }
          } else {
            logInfo { "TC endorsements already regenerated, skipping" }
          }

          // Mark auth key rotation as complete only after ALL post-rotation
          // steps (token refresh, TC endorsement regeneration) have succeeded.
          // The serverAuthRotationCompleted checkpoint already prevents
          // re-calling the server on retry, so a transient failure in token
          // refresh or endorsements will correctly re-enter AuthKeyRotation
          // on resume() and retry from local rotation onward.
          setAuthKeyRotationComplete(state.type).bind()

          // Clear the pending auth rotation data now that everything succeeded.
          w3UpgradeDao.clearPendingAuthRotationData()
            .mapError { MigrationError.StatePersistenceFailed(it) }
            .bind()

          logInfo { "Auth key rotation completed for ${state.type}" }

          return@coroutineBinding state.next(currentKeybox = updatedKeybox)
        }
      }
    }
  }

  private suspend fun rotateAppAuthTokens(
    account: FullAccount,
    newAppAuthKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val recoveryAuthTokens = accountAuthenticator.appAuth(
        appAuthPublicKey = newAppAuthKeys.appRecoveryAuthPublicKey,
        authTokenScope = Recovery
      ).bind().authTokens

      authTokensService
        .setTokens(account.accountId, recoveryAuthTokens, Recovery)
        .bind()

      val globalAuthTokens = accountAuthenticator.appAuth(
        appAuthPublicKey = newAppAuthKeys.appGlobalAuthPublicKey,
        authTokenScope = Global
      ).bind().authTokens

      authTokensService
        .setTokens(account.accountId, globalAuthTokens, Global)
        .bind()
    }

  private suspend fun regenerateAndVerifyTrustedContactEndorsements(
    rotatedAccount: FullAccount,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    oldHwAuthPublicKey: HwAuthPublicKey,
    newHwAuthPublicKey: HwAuthPublicKey,
    newAppAuthKeys: AppAuthPublicKeys,
  ): Result<Unit, Error> =
    coroutineBinding {
      val trustedContacts = relationshipsService
        .getRelationshipsWithoutSyncing(rotatedAccount.accountId)
        .bind()
        .endorsedTrustedContacts
      endorseTrustedContactsService.authenticateRegenerateAndEndorse(
        accountId = rotatedAccount.accountId,
        contacts = trustedContacts,
        oldAppGlobalAuthKey = oldAppGlobalAuthKey,
        oldHwAuthKey = oldHwAuthPublicKey,
        newAppGlobalAuthKey = newAppAuthKeys.appGlobalAuthPublicKey,
        newAppGlobalAuthKeyHwSignature = newAppAuthKeys.appGlobalAuthKeyHwSignature,
        newHwAuthKey = newHwAuthPublicKey
      ).bind()

      relationshipsService.syncAndVerifyRelationships(rotatedAccount).bind()
    }

  private suspend fun handleHardwareDescriptorProvisioning(
    state: MigrationProgress.HardwareDescriptorProvisioning,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val signature = state.appGlobalAuthKeyHwSignature
        ?: error("Hardware descriptor provisioning requires appGlobalAuthKeyHwSignature.")
      val account = getActiveFullAccount().bind()

      val updatedKeybox = keyboxDao.updateAppGlobalAuthKeyHwSignature(account.keybox, signature)
        .mapError { MigrationError.AuthKeyRotationFailed(it) }
        .bind()

      setServerKeysetActive(state.type).bind()

      logInfo { "Hardware descriptor provisioning completed for ${state.type}" }

      state.next(currentKeybox = updatedKeybox)
    }
  }

  private suspend fun handleDdkBackup(
    state: MigrationProgress.DdkBackup,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      val account = getActiveFullAccount().bind()

      val sealedDdkData = state.sealedDdkData
        ?: error("DdkBackup state must have sealedDdkData. Seal the DDK via NFC first.")

      // Upload the sealed DDK to the server
      delegatedDecryptionKeyService.uploadSealedDelegatedDecryptionKeyData(
        fullAccountId = account.accountId,
        sealedData = sealedDdkData
      )
        .mapError { MigrationError.DdkBackupFailed(it) }
        .bind()

      setDdkBackedUp(state.type).bind()

      // Clear the onboarding sealed SSEK only after the DDK checkpoint is durable.
      // Earlier states (ServerKeysetActivation, HardwareDescriptorProvisioning, DdkBackup)
      // all rewind through DescriptorBackup on resume, which needs the SSEK for encryption.
      // CloudBackup is the first state that does NOT replay descriptor backup.
      // Best-effort: if clear fails, the stale SSEK is harmless (sealed, overwritten on
      // next onboarding). Moving clear before the checkpoint would reintroduce the crash
      // window where a retry can't find the SSEK for descriptor backup replay.
      if (state.type == MigrationType.W3Upgrade) {
        onboardingKeyboxSealedSsekDao.clear()
          .logFailure { "Failed to clear onboarding sealed SSEK after DDK backup" }
      }

      logInfo { "DDK re-sealed and uploaded for ${state.type}" }

      state.next()
    }
  }

  private suspend fun handleCloudBackup(
    state: MigrationProgress.CloudBackup,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      setCloudBackupComplete(state.type).bind()

      logInfo { "Cloud backup marked as complete" }

      state.next()
    }
  }

  private suspend fun handleStartDelayNotify(
    state: MigrationProgress.StartDelayNotify,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      // Delay/Notify logic - only for types that require server sweep
      when (state.type) {
        MigrationType.PrivateWalletMigration -> {
          logInfo { "Delay/notify not required for ${state.type}" }
        }
        MigrationType.W3Upgrade -> {
          logInfo { "Delay/notify not required for ${state.type}" }
        }
      }

      state.next()
    }
  }

  private suspend fun handleSweep(
    state: MigrationProgress.Sweep,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      // Sweep logic - only for types that require server sweep
      when (state.type) {
        MigrationType.PrivateWalletMigration -> {
          logInfo { "Server sweep not required for ${state.type}" }
        }
        MigrationType.W3Upgrade -> {
          logInfo { "Server sweep not required for ${state.type}" }
        }
      }

      state.next()
    }
  }

  private suspend fun handleLocalKeyboxActivation(
    state: MigrationProgress.LocalKeyboxActivation,
  ): Result<MigrationProgress, MigrationError> {
    return coroutineBinding {
      // At this point in the migration flow, the new keybox has already been
      // activated locally as the active keybox (see handleCreateNewKeyset).
      // This handler finalizes the migration by marking the sweep as completed.
      setSweepCompleted(state.type).bind()

      logInfo { "Marked sweep as completed for ${state.type}; migration finalized" }

      state.next()
    }
  }

  override suspend fun estimateMigrationFees(
    account: FullAccount,
    oldHardwareFingerprint: String?,
  ): Result<BitcoinMoney, MigrationError> {
    val sweepContext = if (oldHardwareFingerprint != null) {
      SweepContext.W3Upgrade(replacedHardwareFingerprint = oldHardwareFingerprint)
    } else {
      SweepContext.InactiveWallet
    }
    return sweepService.estimateSweepWithMockDestination(account.keybox, sweepContext)
      .map { sweep -> sweep.totalFeeAmount }
      .mapError { error ->
        when (error) {
          is NoFundsToSweep -> MigrationError.InsufficientFundsForMigration
          is SweepGenerationFailed -> MigrationError.FeeEstimationFailed(error.cause)
        }
      }
  }

  override suspend fun clearMigration(type: MigrationType) {
    when (type) {
      MigrationType.PrivateWalletMigration -> privateWalletMigrationDao.clear()
      MigrationType.W3Upgrade -> w3UpgradeDao.clear()
    }
  }

  override suspend fun isW3UpgradeInProgress(
    f8eEnvironment: F8eEnvironment,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Boolean {
    return authF8eClient.initiateAuthentication(
      f8eEnvironment = f8eEnvironment,
      authPublicKey = hwAuthPublicKey
    ).fold(
      success = { false },
      failure = { error ->
        error is HttpError.ClientError && error.response.status == NotFound
      }
    )
  }

  override suspend fun getOldHardwareFingerprint(): Result<String?, MigrationError> {
    return w3UpgradeDao.currentState()
      .first()
      .map { it?.oldHardwareFingerprint }
      .mapError { MigrationError.StatePersistenceFailed(it) }
  }

  private fun missingHistoricalKeysetIds(
    descriptorBackups: List<DescriptorBackup>,
    localKeysets: List<SpendingKeyset>,
  ): Set<String> {
    val localKeysetIds = localKeysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
    return descriptorBackups.map { it.keysetId }.toSet() - localKeysetIds
  }

}

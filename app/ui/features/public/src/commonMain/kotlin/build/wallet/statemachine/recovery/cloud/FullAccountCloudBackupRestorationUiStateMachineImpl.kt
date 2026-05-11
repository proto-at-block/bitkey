package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.CLOUD_BACKUP_PROVISION_APP_AUTH_KEY
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.UNSEAL_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthSignatureMismatch
import build.wallet.auth.AuthTokensService
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.auth.logAuthFailure
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts
import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.f8eEnvironment
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.SocRecV1AccountFeatures
import build.wallet.crypto.PublicKey
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.feature.flags.ReplaceFullWithLiteAccountFeatureFlag
import build.wallet.feature.flags.W3MidUpgradeRecoveryGuardFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.semverToInt
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.detectedDeviceInfo
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.nfc.transaction.ProvisionAppAuthKeyTransactionProvider
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.SocRecChallengeRepository
import build.wallet.recovery.socrec.SocRecStartedChallengeDao
import build.wallet.recovery.socrec.toActions
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.CloudBackupRestorationUiState.*
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.W3UpgradeCheckpointWriter
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal const val START_SOCIAL_RECOVERY_MESSAGE = "Starting Recovery..."

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class FullAccountCloudBackupRestorationUiStateMachineImpl(
  private val accountAuthenticator: AccountAuthenticator,
  private val appInstallationDao: AppInstallationDao,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val authTokensService: AuthTokensService,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val backupRestorer: FullAccountCloudBackupRestorer,
  private val cloudBackupDao: CloudBackupDao,
  private val csekDao: CsekDao,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val deviceTokenManager: DeviceTokenManager,
  private val eventTracker: EventTracker,
  private val keyboxDao: KeyboxDao,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val recoveryChallengeStateMachine: RecoveryChallengeUiStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
  private val socRecChallengeRepository: SocRecChallengeRepository,
  private val relationshipsService: RelationshipsService,
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
  private val uuidGenerator: UuidGenerator,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
  private val migrationService: MigrationService,
  private val w3UpgradeCheckpointWriter: W3UpgradeCheckpointWriter,
  private val replaceFullWithLiteAccountFeatureFlag: ReplaceFullWithLiteAccountFeatureFlag,
  private val existingFullAccountUiStateMachine: ExistingFullAccountUiStateMachine,
  private val provisionAppAuthKeyTransactionProvider: ProvisionAppAuthKeyTransactionProvider,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val hardwareUnlockInfoService: bitkey.firmware.HardwareUnlockInfoService,
  private val selectCloudBackupUiStateMachine: SelectCloudBackupUiStateMachine,
  private val authF8eClient: AuthF8eClient,
  private val w3MidUpgradeRecoveryGuardFeatureFlag: W3MidUpgradeRecoveryGuardFeatureFlag,
) : FullAccountCloudBackupRestorationUiStateMachine {
  @Composable
  override fun model(props: FullAccountCloudBackupRestorationUiProps): ScreenModel {
    var uiState: CloudBackupRestorationUiState by remember {
      mutableStateOf(
        if (replaceFullWithLiteAccountFeatureFlag.isEnabled()) {
          ReplaceWithLiteAccountState
        } else {
          CloudBackupFoundUiState
        }
      )
    }

    // Reusable model for a loading screen while completing multiple restoration steps.
    val loadingRestoringFromBackupModel =
      LoadingBodyModel(
        title = "Restoring from backup...",
        onBack = { uiState = CloudBackupFoundUiState },
        id = CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
      ).asRootScreen()

    return when (val state = uiState) {
      is RecoveryAuthenticationState ->
        recoveryAuthenticationModel(state, setState = { uiState = it })

      is SocRecRestorationState ->
        socRecRestorationModel(
          state,
          props,
          loadingRestoringFromBackupModel,
          setState = { uiState = it }
        )

      is SocRecChallengeState ->
        socRecChallengeModel(state, setState = { uiState = it })

      is CloudBackupFoundUiState ->
        cloudBackupFoundModel(props, setState = { uiState = it })

      is SocialRecoveryExplanationState ->
        socialRecoveryExplanationModel(state, setState = { uiState = it })

      is SelectingSocRecBackupUiState ->
        selectingSocRecBackupModel(state, props.onExit, setState = { uiState = it })

      is UnsealingCsek ->
        unsealingCsekModel(state, props, setState = { uiState = it })

      is RestoringFromBackupUiState ->
        restoringFromBackupModel(
          props,
          state,
          loadingRestoringFromBackupModel,
          setState = { uiState = it }
        )

      is CompletingCloudRecoveryUiState ->
        completingCloudRecoveryModel(props, state, loadingRestoringFromBackupModel, setState = {
          uiState = it
        })

      is RestoringFromBackupFailureUiState ->
        restoringFromBackupFailureModel(state, props)

      is CheckingRecoveryAuthKeyUiState ->
        checkingRecoveryAuthKeyModel(state, props, setState = { uiState = it })

      is RecommendTapOtherBitkeyUiState ->
        recommendTapOtherBitkeyModel(props, setState = { uiState = it })

      is SocRecRestorationFailedState ->
        socRecRestorationFailedModel(state, props, setState = { uiState = it })

      is ReplaceWithLiteAccountState ->
        replaceWithLiteAccountModel(props, setState = { uiState = it })

      is ProvisioningAppAuthKeyUiState ->
        provisioningAppAuthKeyModel(state, props, setState = { uiState = it })

      is SavingKeyboxUiState ->
        savingKeyboxModel(
          props,
          state,
          loadingRestoringFromBackupModel,
          setState = { uiState = it }
        )
    }
  }

  @Composable
  private fun recoveryAuthenticationModel(
    state: RecoveryAuthenticationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    RecoveryAuthenticationEffect(state, setState = setState)
    return LoadingBodyModel(
      title = START_SOCIAL_RECOVERY_MESSAGE,
      onBack = { setState(CloudBackupFoundUiState) },
      id = CloudEventTrackerScreenId.CLOUD_RECOVERY_AUTHENTICATION
    ).asRootScreen()
  }

  @Composable
  private fun socRecRestorationModel(
    state: SocRecRestorationState,
    props: FullAccountCloudBackupRestorationUiProps,
    loadingModel: ScreenModel,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    SocRecRestoreEffect(props, state, setState = setState)
    return loadingModel
  }

  @Composable
  private fun socRecChallengeModel(
    state: SocRecChallengeState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    return recoveryChallengeStateMachine.model(
      RecoveryChallengeUiProps(
        accountId = state.accountId,
        actions =
          socRecChallengeRepository.toActions(
            state.accountId,
            state.isUsingSocRecFakes
          ),
        endorsedTrustedContacts = state.contacts,
        relationshipIdToSocRecPkekMap =
          state.accountFeatures.socRecSealedDekMap
            .mapValues { it.value },
        sealedPrivateKeyMaterial = state.accountFeatures.socRecSealedFullAccountKeys,
        onExit = { setState(CloudBackupFoundUiState) },
        onKeyRecovered = {
          setState(
            SocRecRestorationState(
              accountId = state.accountId,
              it
            )
          )
        }
      )
    )
  }

  @Composable
  private fun cloudBackupFoundModel(
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    // Show social recovery button if ANY backup has social recovery data available
    val showSocRecButton = props.backups.any { it.socRecDataAvailable }

    return CloudBackupFoundModel(
      devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
      onBack = props.onExit,
      onRestore = {
        setState(UnsealingCsek())
      },
      showSocRecButton = showSocRecButton,
      onLostBitkeyClick = {
        // Filter to backups with social recovery data
        val socRecBackups = props.backups.filter { it.socRecDataAvailable }

        if (socRecBackups.size > 1) {
          // Multiple backups with social recovery - show selection
          setState(SelectingSocRecBackupUiState(socRecBackups))
        } else {
          // Single backup with social recovery - proceed directly
          setState(SocialRecoveryExplanationState(socRecBackups.first()))
        }
      }
    ).asRootScreen()
  }

  @Composable
  private fun socialRecoveryExplanationModel(
    state: SocialRecoveryExplanationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    return SocialRecoveryExplanationModel(
      onBack = {
        setState(CloudBackupFoundUiState)
      },
      onContinue = {
        // Use the selected backup for social recovery
        val backup = state.selectedBackup as? SocRecV1BackupFeatures
        val account = backup?.fullAccountFields as? SocRecV1AccountFeatures

        setState(
          if (backup == null || account == null) {
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
                actionDescription = "Reading full account from backup",
                cause = Error("Backup did not contain data for full account")
              ),
              onBack = { setState(SocialRecoveryExplanationState(state.selectedBackup)) },
              failure = CloudBackupFailure.CantFindCloudAccount
            )
          } else {
            RecoveryAuthenticationState(
              accountFeatures = account,
              backupFeatures = backup
            )
          }
        )
      }
    ).asRootScreen()
  }

  @Composable
  private fun selectingSocRecBackupModel(
    state: SelectingSocRecBackupUiState,
    onExit: () -> Unit,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    return selectCloudBackupUiStateMachine.model(
      props = SelectCloudBackupUiProps(
        backups = state.socRecBackups,
        onBackupSelected = { selectedBackup ->
          setState(SocialRecoveryExplanationState(selectedBackup))
        },
        onBack = onExit
      )
    )
  }

  @Composable
  private fun unsealingCsekModel(
    state: UnsealingCsek,
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    // Map backups to their sealed CSEKs so we can track which backup succeeds
    val backupToSealedCsek = props.backups.map { backup ->
      when (backup) {
        is CloudBackupV2, is CloudBackupV3 ->
          backup to (backup.fullAccountFields as FullAccountFields).sealedHwEncryptionKey
      }
    }

    // Captured during the NFC session for deferred persistence after successful unseal.
    // Must be remembered so the value survives recompositions during the W3 two-tap flow.
    var capturedDeviceInfo by remember { mutableStateOf<FirmwareDeviceInfo?>(null) }

    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          // Fetch device info for hardware-type branching only.
          // Don't persist metadata until after successful unseal/transfer
          // to ensure the tapped hardware is verified before updating paired metadata.
          val deviceInfo = commands.detectedDeviceInfo(session)
          val hardwareType = deviceInfo.hardwareType()
          // Capture for deferred persistence after successful unseal
          capturedDeviceInfo = deviceInfo

          when (hardwareType) {
            HardwareType.W1 -> {
              // Try unsealing each CSEK until one succeeds
              var unsealedCsek: Csek? = null
              var successfulBackup: CloudBackup? = null
              var lastError: Throwable? = null

              for ((backup, sealedCsek) in backupToSealedCsek) {
                try {
                  val result = Csek(commands.unsealSymmetricKey(session, sealedCsek))
                  unsealedCsek = result
                  successfulBackup = backup
                  csekDao.set(key = sealedCsek, value = result)
                  break
                } catch (e: NfcException) {
                  lastError = e
                } catch (e: IllegalArgumentException) {
                  lastError = e
                }
              }

              if (unsealedCsek == null || successfulBackup == null) {
                throw lastError ?: Error("Could not unseal any backup with this hardware")
              }

              // Persist device metadata only after successful unseal proves correct device
              syncDeviceMetadata(session, commands, deviceInfo)

              HardwareInteraction.Completed(Pair(unsealedCsek, successfulBackup))
            }

            HardwareType.W3 -> {
              val sealedCseks = backupToSealedCsek.map { (_, sealedCsek) -> sealedCsek }

              commands.requireW3(session).fullAccountCloudBackupRestoration(
                session = session,
                sealedCseks = sealedCseks
              ) { result ->
                val (successfulBackup, sealedCsek) = backupToSealedCsek[result.index]
                val unsealedCsek = Csek(result.unsealedCsek)
                csekDao.set(key = sealedCsek, value = unsealedCsek)

                Pair(unsealedCsek, successfulBackup)
              }
            }
          }
        },
        onSuccess = { (_, successfulBackup) ->
          // Persist device metadata now that unseal has proven correct hardware
          capturedDeviceInfo?.let { persistDeviceMetadata(it) }
          setState(RestoringFromBackupUiState(successfulBackup))
        },
        onCancel = {
          // When entered from the W-17080 blocking modal, bounce NFC cancel
          // back to that modal instead of `CloudBackupFoundUiState` — the
          // menu exposes paths (Social Recovery → auth failure →
          // ProblemWithCloudBackupModel) that can reach Lost App & Cloud
          // via `onRecoverAppKey` and defeat the intended block.
          setState(
            if (state.enteredFromBlockingModal) RecommendTapOtherBitkeyUiState
            else CloudBackupFoundUiState
          )
        },
        onError = { error ->
          handleUnsealError(
            error = error,
            capturedDeviceInfo = capturedDeviceInfo,
            setState = setState
          )
        },
        hardwareVerification = NotRequired,
        screenPresentationStyle = Root,
        eventTrackerContext = UNSEAL_CLOUD_BACKUP,
        confirmationContent = HardwareConfirmationContent.CloudBackupRestoration,
        confirmationResultContent = ConfirmationResultContent(
          pendingHeadline = "Approve on Bitkey",
          pendingSubline = "You'll need to approve on your Bitkey device before tapping again."
        ),
        segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
        actionDescription = "Unsealing CSEK for full account cloud restoration",
        // Always use W3 NfcCommands — they delegate to W1 for all shared commands
        // (getDeviceInfo, unsealSymmetricKey, etc.) and only override W3-specific ones.
        // The session lambda detects actual hardware type via getDeviceInfo and branches.
        // This is a hack that we should improve with better abstractions
        hardwareTypeOverride = HardwareType.W3
      )
    )
  }

  @Composable
  private fun restoringFromBackupModel(
    props: FullAccountCloudBackupRestorationUiProps,
    state: RestoringFromBackupUiState,
    loadingModel: ScreenModel,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    RestoringFromBackupEffect(props, state, setState = setState)
    return loadingModel
  }

  @Composable
  private fun completingCloudRecoveryModel(
    props: FullAccountCloudBackupRestorationUiProps,
    state: CompletingCloudRecoveryUiState,
    loadingModel: ScreenModel,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    CompleteCloudRecoveryEffect(props, state, setState = setState)
    return loadingModel
  }

  @Composable
  private fun restoringFromBackupFailureModel(
    state: RestoringFromBackupFailureUiState,
    props: FullAccountCloudBackupRestorationUiProps,
  ): ScreenModel {
    return ProblemWithCloudBackupModel(
      onBack = state.onBack,
      onRecoverAppKey = props.onRecoverAppKey,
      failure = state.failure
    ).asRootScreen()
  }

  // TODO(W-17080): extract this probe into a dedicated service. The state
  //  machine shouldn't be calling `authF8eClient` directly — this lives here
  //  for now because the W-17080 guard is temporary (removed once LostApp&Cloud
  //  supports the mid-upgrade state).
  @Composable
  private fun checkingRecoveryAuthKeyModel(
    state: CheckingRecoveryAuthKeyUiState,
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    LaunchedEffect("check-recovery-auth-key") {
      // Probe each backup's recovery auth pubkey against the server. The
      // blocking modal fires iff every backup's pubkey is rejected with a
      // 404. Probes run per-backup against each backup's OWN f8eEnvironment
      // (ambient `AccountConfigService` may query the wrong backend during
      // unauthenticated recovery). A null result means the backup didn't
      // carry a recovery auth keypair — treated the same as any other
      // non-404 outcome and falls through to the fallback.
      val recoveryResults = coroutineScope {
        props.backups.map { backup ->
          async {
            (backup as? SocRecV1BackupFeatures)?.let { features ->
              authF8eClient.initiateAuthentication(
                f8eEnvironment = backup.f8eEnvironment,
                authPublicKey = features.appRecoveryAuthKeypair.publicKey,
                tokenScope = AuthTokenScope.Recovery
              )
            }
          }
        }.awaitAll()
      }

      val anyRecoveryPubkeyStillValid = recoveryResults.any { it?.isOk == true }
      val allRecoveryPubkeysRejected = recoveryResults.isNotEmpty() &&
        recoveryResults.all { it?.isClientNotFound() == true }

      val next = when {
        anyRecoveryPubkeyStillValid -> state.fallback
        allRecoveryPubkeysRejected -> {
          logInfo {
            "Detected mid-W3-upgrade mismatch: all cloud backup recovery auth " +
              "pubkeys rejected by server; recommending user tap other hardware"
          }
          RecommendTapOtherBitkeyUiState
        }
        else -> state.fallback
      }
      setState(next)
    }
    // Route Back to props.onExit (not CloudBackupFoundUiState) for the same
    // reason as RecommendTapOtherBitkeyModel below: if the user cancels the
    // probe and falls back to the cloud-backup menu, they can reach Lost
    // App & Cloud via other paths and bypass the W-17080 block.
    return LoadingBodyModel(
      title = "Checking your backup…",
      onBack = props.onExit,
      id = CloudEventTrackerScreenId.CHECKING_RECOVERY_AUTH_KEY
    ).asRootScreen()
  }

  @Composable
  private fun recommendTapOtherBitkeyModel(
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    // Route Back to the parent onExit (not back to CloudBackupFoundUiState).
    // Falling back to CloudBackupFoundUiState would re-expose paths that can
    // reach `ProblemWithCloudBackupModel` and its `onRecoverAppKey` → Lost
    // App & Cloud entry (e.g. via a Social Recovery failure or another
    // restore attempt), defeating the "blocking" intent of this screen.
    // The only forward action is primary "Try a different Bitkey".
    return RecommendTapOtherBitkeyModel(
      onBack = props.onExit,
      onTapOtherBitkey = { setState(UnsealingCsek(enteredFromBlockingModal = true)) }
    ).asRootScreen()
  }

  private fun Result<AuthF8eClient.InitiateAuthenticationSuccess, NetworkingError>.isClientNotFound(): Boolean {
    val error = getError() ?: return false
    return error is HttpError.ClientError && error.response.status == NotFound
  }

  /**
   * Handles the `onError` callback for [unsealingCsekModel]'s NFC session.
   * Returns true if the error was consumed; false lets it propagate.
   *
   * If the tapped hardware is W3 and the [W3MidUpgradeRecoveryGuardFeatureFlag]
   * is enabled, route to [CheckingRecoveryAuthKeyUiState] to probe whether the
   * user is mid-W3-upgrade (W-17080) before falling through to the generic
   * "problem with backup" screen. When the flag is disabled, behavior matches
   * the pre-W-17080 legacy flow.
   */
  private fun handleUnsealError(
    error: NfcException,
    capturedDeviceInfo: FirmwareDeviceInfo?,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): Boolean {
    if (error !is NfcException.CommandErrorSealCsekResponseUnsealException) return false
    val unsealFailure = RestoringFromBackupFailureUiState(
      errorData = ErrorData(
        segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
        actionDescription = "Unsealing CSEK for full account cloud restoration",
        cause = error
      ),
      onBack = { setState(CloudBackupFoundUiState) },
      failure = CloudBackupFailure.HWCantDecryptCSEK
    )
    val guardEnabled = w3MidUpgradeRecoveryGuardFeatureFlag.isEnabled()
    val next = when {
      guardEnabled && capturedDeviceInfo?.hardwareType() == HardwareType.W3 ->
        CheckingRecoveryAuthKeyUiState(fallback = unsealFailure)
      else -> unsealFailure
    }
    setState(next)
    return true
  }

  @Composable
  private fun socRecRestorationFailedModel(
    state: SocRecRestorationFailedState,
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      title = "We were unable to complete your restoration",
      secondaryButton = ButtonDataModel(text = "Back", onClick = props.onExit),
      primaryButton =
        ButtonDataModel(
          text = "Retry",
          onClick = {
            setState(
              SocRecRestorationState(
                accountId = state.accountId,
                fullAccountKeys = state.fullAccountKeys
              )
            )
          }
        ),
      eventTrackerScreenId = CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP,
      errorData = ErrorData(
        segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
        actionDescription = "Restoring full account from backup",
        cause = state.cause
      )
    ).asRootScreen()
  }

  @Composable
  private fun replaceWithLiteAccountModel(
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    // Use the first backup for the UI display
    val primaryBackup = props.backups.first()
    return existingFullAccountUiStateMachine.model(
      ExistingFullAccountUiProps(
        cloudBackup = primaryBackup,
        devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
        onBack = props.onExit,
        onRestore = { setState(UnsealingCsek()) },
        onBackupArchive = props.goToLiteAccountCreation
      )
    )
  }

  @Composable
  private fun provisioningAppAuthKeyModel(
    state: ProvisioningAppAuthKeyUiState,
    props: FullAccountCloudBackupRestorationUiProps,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        transaction = provisionAppAuthKeyTransactionProvider(
          appGlobalAuthPublicKey = state.accountRestoration.activeAppKeyBundle.authKey,
          onSuccess = {
            // Save the keybox as active after successful provisioning
            setState(
              SavingKeyboxUiState(
                accountRestoration = state.accountRestoration,
                fullAccountId = state.fullAccountId
              )
            )
          },
          onCancel = {
            setState(
              RestoringFromBackupFailureUiState(
                errorData = ErrorData(
                  segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                  actionDescription = "Provisioning app auth key to hardware - cancelled",
                  cause = Error("User cancelled NFC provisioning")
                ),
                onBack = props.onExit,
                failure = CloudBackupFailure.AppCantPerformPostRestorationSteps
              )
            )
          }
        ),
        screenPresentationStyle = Root,
        eventTrackerContext = CLOUD_BACKUP_PROVISION_APP_AUTH_KEY,
        hardwareVerification = NotRequired
      )
    )
  }

  @Composable
  private fun savingKeyboxModel(
    props: FullAccountCloudBackupRestorationUiProps,
    state: SavingKeyboxUiState,
    loadingModel: ScreenModel,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ): ScreenModel {
    SavingKeyboxEffect(props, state, setState = setState)
    return loadingModel
  }

  @Composable
  private fun RestoringFromBackupEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: RestoringFromBackupUiState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("restoring-from-backup") {
      backupRestorer
        .restoreFromBackup(cloudBackup = state.successfulBackup)
        .logFailure { "Error restoring keybox from cloud backup" }
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                actionDescription = "Restoring full account from backup",
                cause = it
              ),
              onBack = props.onExit,
              failure = CloudBackupFailure.AppCantRestoreCloudBackup
            )
          )
        }
        .onSuccess { accountRestoration ->
          setState(CompletingCloudRecoveryUiState(accountRestoration))
        }
    }
  }

  @Composable
  private fun CompleteCloudRecoveryEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: CompletingCloudRecoveryUiState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("completing-cloud-recovery") {
      handleCloudKeyRecovered(
        accountRestoration = state.accountRestoration,
        tolerateRecoveryAuthFailureForUpgradeResume = true
      )
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                actionDescription = "Authenticating with new app auth key and applying cloud backup",
                cause = it
              ),
              onBack = props.onExit,
              failure = CloudBackupFailure.AppCantPerformPostRestorationSteps
            )
          )
        }
        .onSuccess { result ->
          when (result.recoveryAuthResult) {
            RecoveryAuthForCloudRestoreResult.UpgradeInProgress -> {
              setState(
                SavingKeyboxUiState(
                  accountRestoration = state.accountRestoration,
                  fullAccountId = result.fullAccountId,
                  upgradeIsInProgress = true
                )
              )
            }
            RecoveryAuthForCloudRestoreResult.Authenticated -> {
              firmwareDeviceInfoDao.getDeviceInfo().get()?.let { deviceInfo ->
                val minFirmwareVersion = fingerprintResetMinFirmwareVersionFeatureFlag.flagValue().value.value
                val currentVersionInt = semverToInt(deviceInfo.version)
                val minVersionInt = semverToInt(minFirmwareVersion)
                if (currentVersionInt >= minVersionInt) {
                  setState(
                    ProvisioningAppAuthKeyUiState(
                      accountRestoration = state.accountRestoration,
                      fullAccountId = result.fullAccountId
                    )
                  )
                } else {
                  setState(
                    SavingKeyboxUiState(
                      accountRestoration = state.accountRestoration,
                      fullAccountId = result.fullAccountId
                    )
                  )
                }
              } ?: run {
                setState(
                  SavingKeyboxUiState(
                    accountRestoration = state.accountRestoration,
                    fullAccountId = result.fullAccountId
                  )
                )
              }
            }
          }
        }
    }
  }

  @Composable
  private fun SocRecRestoreEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: SocRecRestorationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("complete-socrec-restore") {
      coroutineBinding {
        // Use the first backup for social recovery restoration
        val primaryBackup = props.backups.first()
        val restoration =
          backupRestorer.restoreFromBackupWithDecryptedKeys(
            cloudBackup = primaryBackup,
            keysInfo = state.fullAccountKeys
          ).bind()

        // Set the flag to show the replace hardware card nudge
        // this flag is used by the MoneyHomeCardsUiStateMachine
        // and toggled off by the RecoveryInProgressDataStateMachine
        postSocRecTaskRepository.setHardwareReplacementNeeded(true).bind()

        // Remove the completed SocRec Challenge from the database
        socRecStartedChallengeDao.clear()

        // Put us into a state where we can start the hardware recovery flow
        val accountId = handleCloudKeyRecovered(restoration).bind().fullAccountId
        keyboxDao
          .saveKeyboxAsActive(
            restoration.asKeybox(
              keyboxId = uuidGenerator.random(),
              fullAccountId = accountId
            )
          )
          .bind()
      }.onFailure {
        setState(
          SocRecRestorationFailedState(
            accountId = state.accountId,
            fullAccountKeys = state.fullAccountKeys,
            cause = it
          )
        )
      }
    }
  }

  private suspend fun handleCloudKeyRecovered(
    accountRestoration: AccountRestoration,
    tolerateRecoveryAuthFailureForUpgradeResume: Boolean = false,
  ): Result<CloudKeyRecoveredResult, Error> =
    coroutineBinding {
      eventTracker.track(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)

      // Authenticate with f8e using recovered app [Global] authentication key.
      val globalAuthData =
        authenticateWithF8eAndStoreAuthTokens(
          appAuthPublicKey = accountRestoration.activeAppKeyBundle.authKey,
          tokenScope = AuthTokenScope.Global
        ).bind()
      val accountId = FullAccountId(globalAuthData.accountId)

      val recoveryAuthResult =
        authenticateRecoveryAuthForCloudRestore(
          accountRestoration = accountRestoration,
          tolerateRecoveryAuthFailureForUpgradeResume = tolerateRecoveryAuthFailureForUpgradeResume
        ).bind()

      // TODO(W-1535): this should be prompted by a notification prompt
      deviceTokenManager
        .addDeviceTokenIfPresentForAccount(
          fullAccountId = accountId,
          authTokenScope = AuthTokenScope.Global
        )

      cloudBackupDao
        .set(accountId.serverId, accountRestoration.cloudBackupForLocalStorage)
        .bind()

      // Clear out ongoing Lost Hardware DN recovery, if any.
      recoveryStatusService
        .clear()
        .bind()

      // Attempt to sync social relationships before completing the recovery to ensure that
      // the background refresh doesn't delete existing TCs. But don't bind any failures.
      relationshipsService.syncAndVerifyRelationships(
        accountId = accountId,
        appAuthKey = accountRestoration.activeAppKeyBundle.authKey,
        hwAuthPublicKey = accountRestoration.activeHwKeyBundle.authKey
      )

      // Attempt to sync the new wallet before completing the recovery and showing
      // Money Home (saving the keybox as active will complete and update UI), but
      // don't bind any failures.
      appSpendingWalletProvider.getSpendingWallet(accountRestoration.activeSpendingKeyset)
        .onSuccess { it.sync() }

      CloudKeyRecoveredResult(
        fullAccountId = accountId,
        recoveryAuthResult = recoveryAuthResult
      )
    }

  private suspend fun authenticateRecoveryAuthForCloudRestore(
    accountRestoration: AccountRestoration,
    tolerateRecoveryAuthFailureForUpgradeResume: Boolean,
  ): Result<RecoveryAuthForCloudRestoreResult, Error> {
    // Authenticate with F8e first, separately from token storage, so we can
    // distinguish auth-key failures (tolerable during upgrade resume) from
    // local token persistence failures (never tolerable).
    val authResult =
      accountAuthenticator
        .appAuth(
          appAuthPublicKey = accountRestoration.activeAppKeyBundle.recoveryAuthKey,
          authTokenScope = AuthTokenScope.Recovery
        )

    if (authResult.isErr) {
      // Only tolerate AuthSignatureMismatch (key mismatch) for upgrade resume.
      // Transient network errors, protocol errors, etc. should propagate normally
      // rather than being misclassified as an upgrade-in-progress scenario.
      return if (
        tolerateRecoveryAuthFailureForUpgradeResume &&
        authResult.error is AuthSignatureMismatch &&
        migrationService.isW3UpgradeInProgress(
          f8eEnvironment = accountRestoration.config.f8eEnvironment,
          hwAuthPublicKey = accountRestoration.activeHwKeyBundle.authKey
        )
      ) {
        Ok(RecoveryAuthForCloudRestoreResult.UpgradeInProgress)
      } else {
        authResult.logAuthFailure { "Error authenticating with recovery auth key after cloud restore." }
        Err(authResult.error)
      }
    }

    // Auth succeeded — store tokens. Propagate storage failures normally.
    val authData = authResult.value
    val fullAccountId = FullAccountId(authData.accountId)
    return authTokensService
      .setTokens(fullAccountId, authData.authTokens, AuthTokenScope.Recovery)
      .map { RecoveryAuthForCloudRestoreResult.Authenticated }
      .mapError { Error(it) }
  }

  /**
   * Performs auth with f8e using the given [AppAuthPublicKey] and stores the resulting
   * tokens in [AuthTokenDao] keyed by the given [AuthTokenScope]
   */
  private suspend fun authenticateWithF8eAndStoreAuthTokens(
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<AccountAuthenticator.AuthData, Error> {
    return coroutineBinding {
      val authData =
        accountAuthenticator
          .appAuth(
            appAuthPublicKey = appAuthPublicKey,
            authTokenScope = tokenScope
          )
          .logAuthFailure { "Error authenticating with new app auth key after recovery completed." }
          .bind()

      val fullAccountId = FullAccountId(authData.accountId)
      authTokensService
        .setTokens(fullAccountId, authData.authTokens, tokenScope)
        .mapError { Error(it) }
        .bind()

      authData
    }
  }

  @Composable
  private fun RecoveryAuthenticationEffect(
    state: RecoveryAuthenticationState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("lost-bitkey-auth") {
      appPrivateKeyDao.storeAppKeyPair(state.backupFeatures.appRecoveryAuthKeypair)
        .onFailure {
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
                actionDescription = "Storing app recovery auth key",
                cause = it
              ),
              onBack = { setState(CloudBackupFoundUiState) },
              failure = CloudBackupFailure.AppCantPerformPostRestorationSteps
            )
          )
          return@LaunchedEffect
        }

      authenticateWithF8eAndStoreAuthTokens(
        appAuthPublicKey = state.backupFeatures.appRecoveryAuthKeypair.publicKey,
        tokenScope = AuthTokenScope.Recovery
      ).flatMap { authData ->
        relationshipsService
          .getRelationshipsWithoutSyncing(FullAccountId(authData.accountId))
          .map { Pair(authData, it) }
      }.onSuccess { (authData, relationships) ->
        setState(
          SocRecChallengeState(
            accountId = FullAccountId(authData.accountId),
            contacts = relationships.endorsedTrustedContacts.socialRecoveryTrustedContacts()
              .toImmutableList(),
            isUsingSocRecFakes = state.backupFeatures.isUsingSocRecFakes,
            accountFeatures = state.accountFeatures,
            backupFeatures = state.backupFeatures
          )
        )
      }.onFailure {
        setState(
          RestoringFromBackupFailureUiState(
            errorData = ErrorData(
              segment = RecoverySegment.SocRec.ProtectedCustomer.Restoration,
              actionDescription = "Authenticating with new app recovery auth key, storing tokens, and syncing socrec relationships",
              cause = it
            ),
            onBack = { setState(CloudBackupFoundUiState) },
            failure = CloudBackupFailure.AppCantPerformPostRestorationSteps
          )
        )
      }
    }
  }

  @Composable
  private fun SavingKeyboxEffect(
    props: FullAccountCloudBackupRestorationUiProps,
    state: SavingKeyboxUiState,
    setState: (CloudBackupRestorationUiState) -> Unit,
  ) {
    LaunchedEffect("saving-keybox") {
      val keybox = state.accountRestoration.asKeybox(
        uuidGenerator.random(),
        state.fullAccountId
      )
      val saveResult = if (state.upgradeIsInProgress) {
        w3UpgradeCheckpointWriter.persistCloudRestoreCheckpoint(keybox)
          .mapError { Error("Failed to save keybox with cloud restore checkpoint", it) }
      } else {
        keyboxDao.saveKeyboxAsActive(keybox)
      }
      saveResult
        .onSuccess {
          if (!state.upgradeIsInProgress) {
            fullAccountAuthKeyRotationService.recommendKeyRotation()
          }
        }
        .onFailure { error ->
          setState(
            RestoringFromBackupFailureUiState(
              errorData = ErrorData(
                segment = RecoverySegment.CloudBackup.FullAccount.Restoration,
                actionDescription = "Saving keybox as active",
                cause = error
              ),
              onBack = props.onExit,
              failure = CloudBackupFailure.AppCantPerformPostRestorationSteps
            )
          )
        }
    }
  }

  /**
   * Persists device metadata (device info, serial number) after successful unseal.
   * Does not require an NFC session — uses previously fetched device info.
   */
  private suspend fun persistDeviceMetadata(deviceInfo: FirmwareDeviceInfo) {
    firmwareDeviceInfoDao.setDeviceInfo(deviceInfo)
      .logFailure { "Failed to sync firmware device info during cloud recovery" }

    if (deviceInfo.serial.isNotBlank()) {
      appInstallationDao.updateAppInstallationHardwareSerialNumber(deviceInfo.serial)
        .logFailure { "Failed to sync hardware serial number during cloud recovery" }
    } else {
      logError { "Hardware serial number is blank during cloud recovery" }
    }
  }

  /**
   * Syncs device metadata including fingerprints during cloud recovery.
   * Called after unsealing proves we're on the original hardware, reusing the same NFC session.
   */
  private suspend fun syncDeviceMetadata(
    session: NfcSession,
    commands: NfcCommands,
    deviceInfo: FirmwareDeviceInfo,
  ) {
    persistDeviceMetadata(deviceInfo)

    // This will fail on firmware versions that don't support the command (W1 <=1.0.67).
    catchingResult {
      val enrolledFingerprints = commands.getEnrolledFingerprints(session)
      hardwareUnlockInfoService.replaceAllUnlockInfo(enrolledFingerprints.toUnlockInfoList())
    }.logFailure { "Failed to sync fingerprint data during cloud recovery" }
  }
}

private data class CloudKeyRecoveredResult(
  val fullAccountId: FullAccountId,
  val recoveryAuthResult: RecoveryAuthForCloudRestoreResult,
)

private sealed interface RecoveryAuthForCloudRestoreResult {
  data object Authenticated : RecoveryAuthForCloudRestoreResult

  data object UpgradeInProgress : RecoveryAuthForCloudRestoreResult
}

private sealed interface CloudBackupRestorationUiState {
  /**
   * Initial state – found wallet backup on the cloud storage. Confirm with user they want to restore.
   */
  data object CloudBackupFoundUiState : CloudBackupRestorationUiState

  /**
   * Customer has chosen to restore. Show the NFC prompt to unseal the CSEK and,
   * once we know we're talking to the correct hardware, sync the device +
   * biometric metadata.
   * Detecting the hardware type before unsealing. A quick NFC tap to getDeviceInfo.
   *
   * @property enteredFromBlockingModal true if this was entered from the
   * W-17080 "Use your other Bitkey" blocking modal — used to route NFC
   * cancel back to the modal instead of dropping the user on
   * `CloudBackupFoundUiState`, where they could reach Lost App & Cloud via
   * Social Recovery failure paths.
   */
  data class UnsealingCsek(
    val enteredFromBlockingModal: Boolean = false,
  ) : CloudBackupRestorationUiState

  /**
   * Restoring the account from the backup using the CSEK.
   * We also track the hw authentication key since it's needed
   * if the customer wishes to rotate the authentication keys after the
   * cloud backup restoration.
   * @property successfulBackup The backup that was successfully unsealed with the hardware key
   */
  data class RestoringFromBackupUiState(
    val successfulBackup: build.wallet.cloud.backup.CloudBackup,
  ) : CloudBackupRestorationUiState

  /**
   * Failure when restoring the account from the backup.
   */
  data class RestoringFromBackupFailureUiState(
    val errorData: ErrorData,
    val onBack: () -> Unit,
    val failure: CloudBackupFailure,
  ) : CloudBackupRestorationUiState

  /**
   * Transient state after a W3 CSEK unseal failure: verifies whether the
   * cloud backup's recovery auth pubkey still matches the server. We only
   * route to [RecommendTapOtherBitkeyUiState] when every backup's recovery
   * pubkey is rejected with a 404 (the hardware-is-W3 check is already
   * gated before entering this state). Any other outcome falls back to
   * [fallback].
   */
  data class CheckingRecoveryAuthKeyUiState(
    val fallback: RestoringFromBackupFailureUiState,
  ) : CloudBackupRestorationUiState

  /**
   * Blocking screen shown when we've confirmed the user is in the mid-W3-upgrade
   * state. Prompts them to tap their other (W1) Bitkey instead of falling
   * through to Lost App & Cloud recovery. See W-17080.
   */
  data object RecommendTapOtherBitkeyUiState : CloudBackupRestorationUiState

  /**
   * Used at the end of the Cloud restoration flow
   * to rotate the auth keys so the device is now the active device
   */
  data class CompletingCloudRecoveryUiState(
    val accountRestoration: AccountRestoration,
  ) : CloudBackupRestorationUiState

  /**
   * Provisioning the app auth key to the hardware after completing cloud recovery
   */
  data class ProvisioningAppAuthKeyUiState(
    val accountRestoration: AccountRestoration,
    val fullAccountId: FullAccountId,
  ) : CloudBackupRestorationUiState

  /**
   * Saving the keybox as active after provisioning
   */
  data class SavingKeyboxUiState(
    val accountRestoration: AccountRestoration,
    val fullAccountId: FullAccountId,
    val upgradeIsInProgress: Boolean = false,
  ) : CloudBackupRestorationUiState

  /**
   * Showing social recovery explanation screen for the selected backup.
   */
  data class SocialRecoveryExplanationState(
    val selectedBackup: build.wallet.cloud.backup.CloudBackup,
  ) : CloudBackupRestorationUiState

  /**
   * Multiple backups with social recovery data - showing selection screen.
   */
  data class SelectingSocRecBackupUiState(
    val socRecBackups: List<build.wallet.cloud.backup.CloudBackup>,
  ) : CloudBackupRestorationUiState

  /**
   * Uses the recovery key to authenticate and restore an account from
   * a cloud backup.
   */
  data class RecoveryAuthenticationState(
    val accountFeatures: SocRecV1AccountFeatures,
    val backupFeatures: SocRecV1BackupFeatures,
  ) : CloudBackupRestorationUiState

  /**
   * Starts the Social Recovery challenge flow for restoring
   * using trusted contacts instead of hardware.
   */
  data class SocRecChallengeState(
    val accountId: FullAccountId,
    val contacts: ImmutableList<EndorsedTrustedContact>,
    val isUsingSocRecFakes: Boolean,
    val accountFeatures: SocRecV1AccountFeatures,
    val backupFeatures: SocRecV1BackupFeatures,
  ) : CloudBackupRestorationUiState

  /**
   * Starts restoring account data after a successful social recovery.
   */
  data class SocRecRestorationState(
    val accountId: FullAccountId,
    val fullAccountKeys: FullAccountKeys,
  ) : CloudBackupRestorationUiState

  /**
   * The restoration process after a social recovery challenge failed
   * to complete.
   */
  data class SocRecRestorationFailedState(
    val accountId: FullAccountId,
    val fullAccountKeys: FullAccountKeys,
    val cause: Error,
  ) : CloudBackupRestorationUiState

  data object ReplaceWithLiteAccountState : CloudBackupRestorationUiState
}

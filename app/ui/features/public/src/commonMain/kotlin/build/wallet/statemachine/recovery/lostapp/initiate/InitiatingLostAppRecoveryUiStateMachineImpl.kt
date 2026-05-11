package build.wallet.statemachine.recovery.lostapp.initiate

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.privilegedactions.ActionProofService
import bitkey.privilegedactions.ActionProofService.Companion.ACTION_PROOF_VERSION
import bitkey.recovery.DescriptorBackupService
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_AUTH
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.LOST_APP_RECOVERY
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext.APP_RECOVERY
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.cloud.backup.CloudBackup
import build.wallet.logging.logFailure
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwKeyProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwSignedAction
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.LostAppRecoveryContinueParams
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.lostAppRecoverySignChallenge
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.signChallenge
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.recovery.LostAppAndCloudRecoveryService
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.RetreatStyle.Back
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationRationale
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl.CommsVerificationTargetAction.CancelRecovery
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl.CommsVerificationTargetAction.InitiateRecovery
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl.State.*
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import build.wallet.recovery.CancelDelayNotifyRecoveryError.CommsVerificationRequiredError as CancelCommsVerificationRequiredError

/** UI State Machine for navigating the initiation of lost app recovery. */
interface InitiatingLostAppRecoveryUiStateMachine :
  StateMachine<InitiatingLostAppRecoveryUiProps, ScreenModel>

data class InitiatingLostAppRecoveryUiProps(
  val cloudBackups: List<CloudBackup>,
  val onRollback: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class InitiatingLostAppRecoveryUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val enableNotificationsUiStateMachine: EnableNotificationsUiStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
  private val fullAccountCloudBackupRestorationUiStateMachine:
    FullAccountCloudBackupRestorationUiStateMachine,
  private val ssekDao: SsekDao,
  private val descriptorBackupService: DescriptorBackupService,
  private val lostAppAndCloudRecoveryService: LostAppAndCloudRecoveryService,
  private val actionProofService: ActionProofService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val appInstallationDao: AppInstallationDao,
) : InitiatingLostAppRecoveryUiStateMachine {
  @Composable
  override fun model(props: InitiatingLostAppRecoveryUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        if (props.cloudBackups.isEmpty()) {
          AwaitingHardwareKeysState
        } else {
          AttemptingCloudBackupRecoveryState(props.cloudBackups)
        }
      )
    }
    return when (val currentState = state) {
      is AttemptingCloudBackupRecoveryState ->
        fullAccountCloudBackupRestorationUiStateMachine.model(
          props = FullAccountCloudBackupRestorationUiProps(
            backups = currentState.cloudBackups,
            onExit = props.onRollback,
            onRecoverAppKey = { state = AwaitingHardwareKeysState },
            goToLiteAccountCreation = props.goToLiteAccountCreation
          )
        )

      AwaitingHardwareKeysState ->
        RecoverYourAppKeyBodyModel(
          onBack = props.onRollback,
          onStartRecovery = { state = AwaitingNfcForHardwareKeysState }
        ).asRootScreen()

      // Tap 1: Get hardware auth key + detect hardware type
      AwaitingNfcForHardwareKeysState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val authKey = commands.getAuthenticationKey(session)
              val deviceInfo = commands.getDeviceInfo(session)
              // Persist serial for analytics — this is the only NFC tap in the
              // lost-app-and-cloud recovery flow that writes to AppInstallation.
              // FirmwareDeviceInfo is handled by the NFC telemetry interceptor.
              if (deviceInfo.serial.isNotBlank()) {
                appInstallationDao.updateAppInstallationHardwareSerialNumber(deviceInfo.serial)
                  .logFailure { "Failed to sync hardware serial number during lost app recovery" }
              }
              HardwareAuthResult(
                hardwareAuthKey = authKey,
                hardwareType = deviceInfo.hardwareType()
              )
            },
            onSuccess = { result ->
              state = InitiatingHardwareAuthWithF8eState(
                hardwareAuthKey = result.hardwareAuthKey,
                hardwareType = result.hardwareType
              )
            },
            onCancel = { state = AwaitingHardwareKeysState },
            shouldLock = false, // Don't lock because we quickly call signChallenge/signActionProof next
            screenPresentationStyle = Root,
            hardwareVerification = NotRequired,
            eventTrackerContext = NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_GET_HW_KEYS,
            showDeviceConfirmation = true
          )
        )

      is InitiatingHardwareAuthWithF8eState -> {
        LaunchedEffect("request-challenge") {
          lostAppAndCloudRecoveryService
            .initiateAuth(currentState.hardwareAuthKey)
            .onSuccess { authChallenge ->
              state = AwaitingHardwareSignedAuthChallengeState(
                authChallenge = authChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey,
                hardwareType = currentState.hardwareType
              )
            }
            .onFailure { error ->
              state = FailedToInitiateHardwareAuthWithF8eState(currentState.hardwareAuthKey, error)
            }
        }
        // TODO(W-3273): Drop in proper copy and screen for Generating Challenge NFC screen
        LoadingBodyModel(
          title = "Authenticating with server...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE,
          onBack = props.onRollback
        ).asRootScreen()
      }

      is FailedToInitiateHardwareAuthWithF8eState ->
        InitiateRecoveryErrorScreenModel(
          cause = currentState.error,
          onDoneClicked = props.onRollback
        )

      is AwaitingHardwareSignedAuthChallengeState ->
        when (currentState.hardwareType) {
          HardwareType.W1 -> w1SignAuthChallengeModel(currentState) { state = it }
          HardwareType.W3 -> w3SignAuthChallengeModel(currentState) { state = it }
        }

      is AuthenticatingWithF8eViaHardwareState -> {
        LaunchedEffect("authenticate-with-hardware") {
          withMinimumDelay(minimumLoadingDuration.value) {
            lostAppAndCloudRecoveryService
              .completeAuth(
                accountId = FullAccountId(currentState.authChallenge.accountId),
                session = currentState.authChallenge.session,
                hwSignedChallenge = currentState.signedAuthChallenge,
                hwAuthKey = currentState.hardwareAuthKey
              )
          }
            .onSuccess { completedAuth ->
              state = AwaitingHardwareProofOfPossessionAndSpendingKeyState(
                authChallenge = currentState.authChallenge,
                completedAuth = completedAuth,
                signedAuthChallenge = currentState.signedAuthChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey,
                hardwareType = currentState.hardwareType
              )
            }
            .onFailure { error ->
              state = FailedToAuthenticateWithF8eViaHardwareState(
                authChallenge = currentState.authChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey,
                signedAuthChallenge = currentState.signedAuthChallenge,
                error = error
              )
            }
        }
        // TODO(W-3273): Drop in proper copy and screen for Authenticating screen
        LoadingBodyModel(
          title = "Authenticating with hardware...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E,
          onBack = props.onRollback
        ).asRootScreen()
      }

      is FailedToAuthenticateWithF8eViaHardwareState ->
        InitiateRecoveryErrorScreenModel(
          cause = currentState.error,
          onDoneClicked = props.onRollback
        )

      // Tap 3: Get proof of possession + spending key
      is AwaitingHardwareProofOfPossessionAndSpendingKeyState ->
        when (currentState.hardwareType) {
          HardwareType.W1 -> w1HardwareProofAndSpendingKeyModel(currentState) { state = it }
          HardwareType.W3 -> w3CompositeRecoveryModel(currentState) { state = it }
        }

      is AwaitingPushNotificationPermissionState ->
        enableNotificationsUiStateMachine.model(
          props = EnableNotificationsUiProps(
            retreat = Retreat(
              style = Back,
              onRetreat = { state = AwaitingHardwareKeysState }
            ),
            eventTrackerContext = APP_RECOVERY,
            rationale = NotificationRationale.Recovery,
            onComplete = {
              state = InitiatingRecoveryWithF8eState(
                completedAuth = currentState.completedAuth,
                hardwareKeys = currentState.hardwareKeys,
                signedAuthChallenge = currentState.signedAuthChallenge,
                hardwareType = currentState.hardwareType
              )
            }
          )
        ).asRootScreen()

      is InitiatingRecoveryWithF8eState -> {
        LaunchedEffect("initiate-recovery") {
          lostAppAndCloudRecoveryService
            .initiateRecovery(
              completedAuth = currentState.completedAuth,
              hardwareKeysForRecovery = currentState.hardwareKeys
            )
            .onFailure { error ->
              state = when (error) {
                is CommsVerificationRequiredError ->
                  VerifyingNotificationCommsState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    targetAction = InitiateRecovery,
                    hardwareType = currentState.hardwareType
                  )
                is RecoveryAlreadyExistsError ->
                  DisplayingConflictingRecoveryState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    hardwareType = currentState.hardwareType
                  )
                is OtherError ->
                  FailedToInitiateRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    error = error
                  )
              }
            }
        }
        LoadingBodyModel(
          title = "Initiating recovery...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,
          onBack = props.onRollback
        ).asRootScreen()
      }

      is FailedToInitiateRecoveryWithF8eState ->
        InitiateRecoveryErrorScreenModel(
          cause = currentState.error,
          onDoneClicked = props.onRollback
        )

      is VerifyingNotificationCommsState ->
        recoveryNotificationVerificationUiStateMachine.model(
          props = RecoveryNotificationVerificationUiProps(
            fullAccountId = currentState.completedAuth.accountId,
            localLostFactor = App,
            onRollback = props.onRollback,
            onComplete = {
              // We try our target action on F8e again, now that the additional verification
              // is complete
              state = when (currentState.targetAction) {
                InitiateRecovery ->
                  InitiatingRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    hardwareType = currentState.hardwareType
                  )
                is CancelRecovery ->
                  CancellingConflictingRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    cancelProof = currentState.targetAction.cancelProof,
                    hardwareType = currentState.hardwareType
                  )
              }
            }
          )
        )

      is CancellingConflictingRecoveryWithF8eState -> {
        LaunchedEffect("cancel-existing-recovery") {
          lostAppAndCloudRecoveryService
            .cancelRecovery(
              accountId = currentState.completedAuth.accountId,
              proof = currentState.cancelProof
            )
            .onSuccess {
              state = InitiatingRecoveryWithF8eState(
                completedAuth = currentState.completedAuth,
                hardwareKeys = currentState.hardwareKeys,
                signedAuthChallenge = currentState.signedAuthChallenge,
                hardwareType = currentState.hardwareType
              )
            }
            .onFailure { error ->
              state = when (error) {
                is CancelCommsVerificationRequiredError ->
                  VerifyingNotificationCommsState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    targetAction = CancelRecovery(currentState.cancelProof),
                    hardwareType = currentState.hardwareType
                  )
                else ->
                  FailedToCancelConflictingRecoveryState(
                    error = error,
                    hardwareKeys = currentState.hardwareKeys,
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge
                  )
              }
            }
        }
        LoadingBodyModel(
          title = "Cancelling Existing Recovery",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
        ).asRootScreen()
      }

      is DisplayingConflictingRecoveryState ->
        RecoveryConflictModel(
          cancelingRecoveryLostFactor = Hardware,
          onCancelRecovery = {
            state = AwaitingCancelProofState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge,
              hardwareType = currentState.hardwareType
            )
          },
          presentationStyle = Root
        )

      is AwaitingCancelProofState ->
        if (currentState.hardwareType == HardwareType.W3) {
          w3CancelProofModel(currentState) { state = it }
        } else {
          w1CancelProofModel(currentState) { state = it }
        }

      is FailedToCancelConflictingRecoveryState ->
        CancelConflictingRecoveryErrorScreenModel(
          error = currentState.error,
          onDoneClicked = { state = AwaitingHardwareKeysState }
        )
    }
  }

  /**
   * W1 path for Tap 2: sign auth challenge using simple signChallenge command.
   */
  @Composable
  private fun w1SignAuthChallengeModel(
    currentState: AwaitingHardwareSignedAuthChallengeState,
    onStateChange: (State) -> Unit,
  ): ScreenModel =
    nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.signChallenge(session, currentState.authChallenge.challenge)
        },
        onSuccess = { signedChallenge ->
          onStateChange(
            AuthenticatingWithF8eViaHardwareState(
              authChallenge = currentState.authChallenge,
              hardwareAuthKey = currentState.hardwareAuthKey,
              hardwareType = currentState.hardwareType,
              signedAuthChallenge = signedChallenge
            )
          )
        },
        onCancel = { onStateChange(AwaitingHardwareKeysState) },
        hardwareVerification = NotRequired,
        shouldLock = false,
        eventTrackerContext = APP_DELAY_NOTIFY_SIGN_AUTH,
        screenPresentationStyle = Root,
        hardwareTypeOverride = currentState.hardwareType
      )
    )

  /**
   * W3 path for Tap 2: sign auth challenge using confirmable lostAppRecoverySignChallenge command.
   * Shows "Confirm this is your account?" on the device screen before signing.
   */
  @Composable
  private fun w3SignAuthChallengeModel(
    currentState: AwaitingHardwareSignedAuthChallengeState,
    onStateChange: (State) -> Unit,
  ): ScreenModel =
    nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          commands.requireW3(session).lostAppRecoverySignChallenge(session, currentState.authChallenge.challenge)
        },
        onSuccess = { signedChallenge ->
          onStateChange(
            AuthenticatingWithF8eViaHardwareState(
              authChallenge = currentState.authChallenge,
              hardwareAuthKey = currentState.hardwareAuthKey,
              hardwareType = currentState.hardwareType,
              signedAuthChallenge = signedChallenge
            )
          )
        },
        onCancel = { onStateChange(AwaitingHardwareKeysState) },
        hardwareVerification = NotRequired,
        shouldLock = false,
        eventTrackerContext = APP_DELAY_NOTIFY_SIGN_AUTH,
        screenPresentationStyle = Root,
        confirmationContent = HardwareConfirmationContent.LostAppRecoverySignChallenge,
        confirmationResultContent = signChallengeConfirmationContent,
        hardwareTypeOverride = currentState.hardwareType
      )
    )

  /**
   * W1 path for Tap 3: multi-command NFC session with signAccessToken, unsealSymmetricKey,
   * getNextSpendingKey, and signChallenge.
   */
  @Composable
  private fun w1HardwareProofAndSpendingKeyModel(
    currentState: AwaitingHardwareProofOfPossessionAndSpendingKeyState,
    onStateChange: (State) -> Unit,
  ): ScreenModel =
    nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          val proof = HwFactorProofOfPossession(
            commands.signAccessToken(session, currentState.completedAuth.authTokens.accessToken)
          )
          val bitcoinNetwork = currentState.completedAuth.bitcoinNetworkType

          extractHardwareSpendingKeys(
            session = session,
            commands = commands,
            completedAuth = currentState.completedAuth
          ).fold(
            success = { existingKeys ->
              val spendingKey = commands.getNextSpendingKey(
                session = session,
                existingDescriptorPublicKeys = existingKeys,
                network = bitcoinNetwork
              )

              // Sign the new app global auth key with the hardware auth key.
              val appGlobalAuthKeyHwSignature = commands
                .signChallenge(
                  session,
                  currentState.completedAuth.destinationAppKeys.authKey.value
                )
                .let(::AppGlobalAuthKeyHwSignature)

              RotateHwKeysResponse.Success(
                proof = proof,
                spendingKey = spendingKey,
                appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
              )
            },
            failure = { error ->
              RotateHwKeysResponse.Failure(error)
            }
          )
        },
        onSuccess = { result ->
          when (result) {
            is RotateHwKeysResponse.Success ->
              onStateChange(
                AwaitingPushNotificationPermissionState(
                  signedAuthChallenge = currentState.signedAuthChallenge,
                  completedAuth = currentState.completedAuth,
                  hardwareKeys = lostAppAndCloudRecoveryService.buildHardwareKeys(
                    proof = HwKeyProof(result.proof),
                    hardwareAuthKey = currentState.hardwareAuthKey,
                    spendingKey = result.spendingKey,
                    appGlobalAuthKeyHwSignature = result.appGlobalAuthKeyHwSignature,
                    bitcoinNetworkType = currentState.completedAuth.bitcoinNetworkType,
                    hardwareType = currentState.hardwareType
                  ),
                  hardwareType = currentState.hardwareType
                )
              )
            is RotateHwKeysResponse.Failure ->
              onStateChange(AwaitingHardwareKeysState)
          }
        },
        onCancel = { onStateChange(AwaitingHardwareKeysState) },
        hardwareVerification = NotRequired,
        eventTrackerContext = HW_PROOF_OF_POSSESSION,
        screenPresentationStyle = Root,
        hardwareTypeOverride = currentState.hardwareType
      )
    )

  /**
   * W3 path for Tap 3: composite lost_app_recovery confirmable action.
   * Two-tap flow: send sealed SSEK → confirm on device → continue with action proof + key derivation.
   */
  @Composable
  private fun w3CompositeRecoveryModel(
    currentState: AwaitingHardwareProofOfPossessionAndSpendingKeyState,
    onStateChange: (State) -> Unit,
  ): ScreenModel {
    val completedAuth = currentState.completedAuth
    check(completedAuth is CompletedAuth.WithDescriptorBackups) {
      "W3 accounts must use descriptor backups for lost app recovery"
    }

    val nonce = remember { actionProofService.generateNonce() }

    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          val bindings = actionProofService.buildBindings(
            nonce = nonce,
            accountId = completedAuth.accountId
          ).getOrThrow()

          commands.requireW3(session).lostAppRecovery(
            session = session,
            sealedSsek = completedAuth.wrappedSsek,
            onSsekUnsealed = { unsealedSsek ->
              // Store the unsealed SSEK for future use.
              ssekDao.set(completedAuth.wrappedSsek, Sek(unsealedSsek)).getOrThrow()

              // Decrypt descriptor backups to extract existing HW spending keys.
              val keysets = descriptorBackupService.unsealDescriptors(
                sealedSsek = completedAuth.wrappedSsek,
                encryptedDescriptorBackups = completedAuth.descriptorBackups
              ).getOrThrow()

              LostAppRecoveryContinueParams(
                actionProofVersion = ACTION_PROOF_VERSION,
                actionProofAction = ActionProofAction.CREATE_LOST_APP_RECOVERY,
                actionProofBindings = bindings,
                existingHwSpendingKeys = keysets.map { it.hardwareKey },
                network = completedAuth.bitcoinNetworkType,
                appGlobalAuthKey = completedAuth.destinationAppKeys.authKey
              )
            }
          )
        },
        onSuccess = { result ->
          // HW returns raw compact (r||s) 64-byte signature, already hex-encoded.
          val hwSigCompact = result.actionProofSignature.lowercase()

          // Only include HW signature — the new app auth key isn't registered
          // with the server yet, and delay-notify uses AnyFactor (hw suffices).
          val header = actionProofService.createActionProofHeader(
            signatures = listOf(hwSigCompact),
            nonce = nonce
          ).getOrThrow()

          val spendingKey = HwSpendingPublicKey(result.spendingKeyDpub)

          // App auth key HW signature is already hex-encoded.
          val appAuthKeySigHex = result.appAuthKeySignature

          onStateChange(
            AwaitingPushNotificationPermissionState(
              signedAuthChallenge = currentState.signedAuthChallenge,
              completedAuth = completedAuth,
              hardwareKeys = lostAppAndCloudRecoveryService.buildHardwareKeys(
                proof = HwSignedAction(actionProof = header),
                hardwareAuthKey = currentState.hardwareAuthKey,
                spendingKey = spendingKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(appAuthKeySigHex),
                bitcoinNetworkType = completedAuth.bitcoinNetworkType,
                hardwareType = currentState.hardwareType
              ),
              hardwareType = currentState.hardwareType
            )
          )
        },
        onCancel = { onStateChange(AwaitingHardwareKeysState) },
        hardwareVerification = NotRequired,
        eventTrackerContext = LOST_APP_RECOVERY,
        screenPresentationStyle = Root,
        confirmationContent = HardwareConfirmationContent.LostAppRecovery,
        confirmationResultContent = recoveryConfirmationContent,
        hardwareTypeOverride = currentState.hardwareType
      )
    )
  }

  /** W1 cancel proof: sign access token to produce a generic HwKeyProof. */
  @Composable
  private fun w1CancelProofModel(
    currentState: AwaitingCancelProofState,
    onStateChange: (State) -> Unit,
  ): ScreenModel =
    nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          HwFactorProofOfPossession(
            commands.signAccessToken(session, currentState.completedAuth.authTokens.accessToken)
          )
        },
        onSuccess = { proof ->
          onStateChange(
            CancellingConflictingRecoveryWithF8eState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge,
              cancelProof = HwKeyProof(proof),
              hardwareType = currentState.hardwareType
            )
          )
        },
        onCancel = {
          onStateChange(
            DisplayingConflictingRecoveryState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge,
              hardwareType = currentState.hardwareType
            )
          )
        },
        hardwareVerification = NotRequired,
        eventTrackerContext = HW_PROOF_OF_POSSESSION,
        screenPresentationStyle = Root,
        hardwareTypeOverride = currentState.hardwareType
      )
    )

  /** W3 cancel proof: build and sign a structured action proof for the correct cancel action. */
  @Composable
  private fun w3CancelProofModel(
    currentState: AwaitingCancelProofState,
    onStateChange: (State) -> Unit,
  ): ScreenModel {
    val nonce = remember { actionProofService.generateNonce() }

    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          val bindings = actionProofService.buildBindings(
            nonce = nonce,
            accountId = currentState.completedAuth.accountId
          ).getOrThrow()

          commands.requireW3(session).signActionProof(
            session = session,
            version = 1u,
            action = ActionProofAction.CANCEL_CONFLICTING_RECOVERY,
            value = null,
            bindings = bindings
          )
        },
        onSuccess = { hwSignature ->
          val header = actionProofService.createActionProofHeader(
            signatures = listOf(hwSignature.lowercase()),
            nonce = nonce
          ).getOrThrow()

          onStateChange(
            CancellingConflictingRecoveryWithF8eState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge,
              cancelProof = HwSignedAction(actionProof = header),
              hardwareType = currentState.hardwareType
            )
          )
        },
        onCancel = {
          onStateChange(
            DisplayingConflictingRecoveryState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge,
              hardwareType = currentState.hardwareType
            )
          )
        },
        hardwareVerification = NotRequired,
        eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_ACTION_PROOF,
        screenPresentationStyle = Root,
        confirmationContent = HardwareConfirmationContent.SignActionProof,
        confirmationResultContent = ConfirmationResultContent(
          pendingHeadline = "Review action on Bitkey",
          pendingSubline = "Approve on your Bitkey device to cancel the conflicting recovery."
        ),
        showNativeSheetOnIos = false,
        hardwareTypeOverride = currentState.hardwareType,
        showDeviceConfirmation = true
      )
    )
  }

  /** W1: extract hardware spending keys from completedAuth (with NFC for descriptor backups). */
  private suspend fun extractHardwareSpendingKeys(
    session: NfcSession,
    commands: NfcCommands,
    completedAuth: CompletedAuth,
  ): Result<List<HwSpendingPublicKey>, Throwable> {
    return when (completedAuth) {
      is CompletedAuth.WithDirectKeys -> {
        Ok(completedAuth.existingHwSpendingKeys)
      }

      is CompletedAuth.WithDescriptorBackups -> {
        coroutineBinding {
          val unsealedSsek = commands.unsealSymmetricKey(session, completedAuth.wrappedSsek)
          ssekDao.set(completedAuth.wrappedSsek, Sek(unsealedSsek)).bind()

          val keysets = descriptorBackupService.unsealDescriptors(
            sealedSsek = completedAuth.wrappedSsek,
            encryptedDescriptorBackups = completedAuth.descriptorBackups
          ).bind()

          keysets.map { keyset -> keyset.hardwareKey }
        }
      }
    }
  }

  private val recoveryConfirmationContent = ConfirmationResultContent(
    pendingHeadline = "Confirm recovery on Bitkey",
    pendingSubline = "You\u2019ll need to approve or deny on your Bitkey device before tapping again."
  )

  private val signChallengeConfirmationContent = ConfirmationResultContent(
    pendingHeadline = "Confirm on Bitkey",
    pendingSubline = "You\u2019ll need to approve or deny on your Bitkey device before tapping again."
  )

  private fun InitiateRecoveryErrorScreenModel(
    cause: Throwable,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    ErrorFormBodyModel(
      title = "We couldn't initiate recovery process.",
      primaryButton = ButtonDataModel(text = "OK", onClick = onDoneClicked),
      errorData = ErrorData(
        segment = RecoverySegment.DelayAndNotify.LostApp.Initiation,
        actionDescription = "Initiating lost app recovery",
        cause = cause
      ),
      eventTrackerScreenId = LOST_APP_DELAY_NOTIFY_INITIATION_ERROR
    ).asRootScreen()

  private fun CancelConflictingRecoveryErrorScreenModel(
    error: Error,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    ErrorFormBodyModel(
      title = "We couldn't cancel the existing recovery. Please try your recovery again.",
      primaryButton = ButtonDataModel(text = "OK", onClick = onDoneClicked),
      errorData = ErrorData(
        segment = RecoverySegment.DelayAndNotify.LostApp.Cancellation,
        actionDescription = "Cancelling conflicting recovery",
        cause = error
      ),
      eventTrackerScreenId = LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR
    ).asRootScreen()

  /** Result of Tap 1 NFC session: auth key + detected hardware type. */
  internal data class HardwareAuthResult(
    val hardwareAuthKey: HwAuthPublicKey,
    val hardwareType: HardwareType,
  )

  private sealed interface State {
    data class AttemptingCloudBackupRecoveryState(
      val cloudBackups: List<CloudBackup>,
    ) : State

    data object AwaitingHardwareKeysState : State

    data object AwaitingNfcForHardwareKeysState : State

    data class InitiatingHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
      val hardwareType: HardwareType,
    ) : State

    data class FailedToInitiateHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
      val error: Error,
    ) : State

    data class AwaitingHardwareSignedAuthChallengeState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: InitiateAuthenticationSuccess,
      val hardwareType: HardwareType,
    ) : State

    data class AuthenticatingWithF8eViaHardwareState(
      val authChallenge: InitiateAuthenticationSuccess,
      val hardwareAuthKey: HwAuthPublicKey,
      val hardwareType: HardwareType,
      val signedAuthChallenge: String,
    ) : State

    data class FailedToAuthenticateWithF8eViaHardwareState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: InitiateAuthenticationSuccess,
      val signedAuthChallenge: String,
      val error: Throwable,
    ) : State

    data class AwaitingHardwareProofOfPossessionAndSpendingKeyState(
      val authChallenge: InitiateAuthenticationSuccess,
      val completedAuth: CompletedAuth,
      val hardwareAuthKey: HwAuthPublicKey,
      val hardwareType: HardwareType,
      val signedAuthChallenge: String,
    ) : State

    data class AwaitingPushNotificationPermissionState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val hardwareType: HardwareType,
    ) : State

    data class InitiatingRecoveryWithF8eState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val signedAuthChallenge: String,
      val hardwareType: HardwareType,
    ) : State

    data class FailedToInitiateRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val error: Error,
    ) : State

    data class VerifyingNotificationCommsState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val targetAction: CommsVerificationTargetAction,
      val hardwareType: HardwareType,
    ) : State

    data class AwaitingCancelProofState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val signedAuthChallenge: String,
      val hardwareType: HardwareType,
    ) : State

    data class CancellingConflictingRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val cancelProof: PrivilegedActionProof,
      val hardwareType: HardwareType,
    ) : State

    data class DisplayingConflictingRecoveryState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val signedAuthChallenge: String,
      val hardwareType: HardwareType,
    ) : State

    data class FailedToCancelConflictingRecoveryState(
      val error: Error,
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
    ) : State
  }

  /**
   * Comms verification could be required for multiple actions. These actions are enumerated here.
   */
  private sealed interface CommsVerificationTargetAction {
    data class CancelRecovery(
      val cancelProof: PrivilegedActionProof,
    ) : CommsVerificationTargetAction

    data object InitiateRecovery : CommsVerificationTargetAction
  }

  internal sealed interface RotateHwKeysResponse {
    data class Success(
      val proof: HwFactorProofOfPossession,
      val spendingKey: HwSpendingPublicKey,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : RotateHwKeysResponse

    data class Failure(val error: Throwable) : RotateHwKeysResponse
  }
}

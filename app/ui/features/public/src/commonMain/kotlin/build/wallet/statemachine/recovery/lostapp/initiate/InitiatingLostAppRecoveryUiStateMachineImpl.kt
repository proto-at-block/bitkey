package build.wallet.statemachine.recovery.lostapp.initiate

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_AUTH
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext.APP_RECOVERY
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.signChallenge
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.RetreatStyle.Back
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationRationale
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.inprogress.RecoverYourMobileKeyBodyModel
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl.UiState.InitiatingViaNfcState
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachineImpl.UiState.ShowingInstructionsState
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine

/** UI State Machine for navigating the initiation of lost app recovery. */
interface InitiatingLostAppRecoveryUiStateMachine :
  StateMachine<InitiatingLostAppRecoveryUiProps, ScreenModel>

data class InitiatingLostAppRecoveryUiProps(
  val initiatingLostAppRecoveryData: InitiatingLostAppRecoveryData,
)

@BitkeyInject(ActivityScope::class)
class InitiatingLostAppRecoveryUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val enableNotificationsUiStateMachine: EnableNotificationsUiStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
  private val accountConfigService: AccountConfigService,
) : InitiatingLostAppRecoveryUiStateMachine {
  @Composable
  override fun model(props: InitiatingLostAppRecoveryUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(ShowingInstructionsState) }
    return when (val recoveryData = props.initiatingLostAppRecoveryData) {
      is AwaitingHwKeysData -> {
        when (uiState) {
          ShowingInstructionsState ->
            RecoverYourMobileKeyBodyModel(
              onBack = recoveryData.rollback,
              onStartRecovery = {
                uiState = InitiatingViaNfcState
              }
            ).asRootScreen()
          InitiatingViaNfcState ->
            nfcSessionUIStateMachine.model(
              NfcSessionUIStateMachineProps(
                session = { session, commands -> commands.getAuthenticationKey(session) },
                onSuccess = { recoveryData.addHardwareAuthKey(it) },
                onCancel = { uiState = ShowingInstructionsState },
                shouldLock = false, // Don't lock because we quickly call [SignChallenge] next
                screenPresentationStyle = Root,
                eventTrackerContext = NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_GET_HW_KEYS
              )
            )
        }
      }

      is AwaitingPushNotificationPermissionData ->
        enableNotificationsUiStateMachine.model(
          props =
            EnableNotificationsUiProps(
              retreat =
                Retreat(
                  style = Back,
                  onRetreat = {
                    uiState = ShowingInstructionsState
                    recoveryData.onRetreat()
                  }
                ),
              eventTrackerContext = APP_RECOVERY,
              rationale = NotificationRationale.Recovery,
              onComplete = recoveryData.onComplete
            )
        ).asRootScreen()

      // TODO(W-3273): Drop in proper copy and screen for Authenticating screen
      is AuthenticatingWithF8EViaAppData ->
        LoadingBodyModel(
          message = "Authenticating with hardware...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E,
          onBack = recoveryData.rollback
        ).asRootScreen()

      is FailedToAuthenticateWithF8EViaAppData ->
        InitiateRecoveryErrorScreenModel(
          cause = recoveryData.error,
          onDoneClicked = recoveryData.rollback
        )

      // TODO(W-3273): Drop in proper copy and screen for Generating Challenge NFC screen
      is InitiatingAppAuthWithF8eData ->
        LoadingBodyModel(
          message = "Authenticating with server...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE,
          onBack = recoveryData.rollback
        ).asRootScreen()

      is FailedToInitiateAppAuthWithF8eData ->
        InitiateRecoveryErrorScreenModel(
          cause = recoveryData.error,
          onDoneClicked = recoveryData.rollback
        )

      is AwaitingAppSignedAuthChallengeData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.signChallenge(session, recoveryData.challenge.challenge)
            },
            onSuccess = { recoveryData.addSignedChallenge(it) },
            onCancel = {
              uiState = ShowingInstructionsState
              recoveryData.rollback()
            },
            shouldLock = false, // Don't lock because we quickly call [GetNextSpendingKey] next
            eventTrackerContext = APP_DELAY_NOTIFY_SIGN_AUTH,
            screenPresentationStyle = Root
          )
        )

      is AwaitingHardwareProofOfPossessionAndKeysData -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val proof = HwFactorProofOfPossession(
                commands.signAccessToken(session, recoveryData.completedAuth.authTokens.accessToken)
              )
              val bitcoinNetwork = accountConfigService.defaultConfig().value.bitcoinNetworkType
              val spendingKey = commands.getNextSpendingKey(
                session = session,
                existingDescriptorPublicKeys = recoveryData.completedAuth.existingHwSpendingKeys,
                network = bitcoinNetwork
              )

              // Sign the new app global auth key with the hardware auth key.
              val appGlobalAuthKeyHwSignature = commands
                .signChallenge(session, recoveryData.completedAuth.destinationAppKeys.authKey.value)
                .let(::AppGlobalAuthKeyHwSignature)

              RotateHwKeysResponse(proof, spendingKey, appGlobalAuthKeyHwSignature)
            },
            onSuccess = { (proof, spendingKey, appGlobalAuthKeyHwSignature) ->
              recoveryData.onComplete(proof, spendingKey, appGlobalAuthKeyHwSignature)
            },
            onCancel = {
              uiState = ShowingInstructionsState
              recoveryData.rollback()
            },
            eventTrackerContext = HW_PROOF_OF_POSSESSION,
            screenPresentationStyle = Root
          )
        )
      }

      is InitiatingLostAppRecoveryWithF8eData ->
        LoadingBodyModel(
          message = "Initiating recovery...",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,
          onBack = recoveryData.rollback
        ).asRootScreen()

      is FailedToInitiateLostAppWithF8eData ->
        InitiateRecoveryErrorScreenModel(
          cause = recoveryData.error,
          onDoneClicked = recoveryData.rollback
        )

      is VerifyingNotificationCommsData ->
        recoveryNotificationVerificationUiStateMachine.model(
          props =
            RecoveryNotificationVerificationUiProps(
              fullAccountId = recoveryData.fullAccountId,
              localLostFactor = App,
              hwFactorProofOfPossession = recoveryData.hwFactorProofOfPossession,
              onRollback = recoveryData.onRollback,
              onComplete = recoveryData.onComplete
            )
        )

      is DisplayingConflictingRecoveryData ->
        RecoveryConflictModel(
          cancelingRecoveryLostFactor = Hardware,
          onCancelRecovery = recoveryData.onCancelRecovery,
          presentationStyle = Root
        )

      CancellingConflictingRecoveryData ->
        LoadingBodyModel(
          message = "Cancelling Existing Recovery",
          id = LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
        ).asRootScreen()

      is FailedToCancelConflictingRecoveryData ->
        CancelConflictingRecoveryErrorScreenModel(
          error = recoveryData.cause,
          onDoneClicked = recoveryData.onAcknowledge
        )
    }
  }

  private fun InitiateRecoveryErrorScreenModel(
    cause: Throwable,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    ErrorFormBodyModel(
      title = "We couldn’t initiate recovery process.",
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
      title = "We couldn’t cancel the existing recovery. Please try your recovery again.",
      primaryButton = ButtonDataModel(text = "OK", onClick = onDoneClicked),
      errorData = ErrorData(
        segment = RecoverySegment.DelayAndNotify.LostApp.Cancellation,
        actionDescription = "Cancelling conflicting recovery",
        cause = error
      ),
      eventTrackerScreenId = LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR
    ).asRootScreen()

  private sealed interface UiState {
    data object ShowingInstructionsState : UiState

    data object InitiatingViaNfcState : UiState
  }

  private data class RotateHwKeysResponse(
    val proof: HwFactorProofOfPossession,
    val spendingKey: HwSpendingPublicKey,
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  )
}

package build.wallet.statemachine.recovery.lostapp.initiate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_AUTH
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext.APP_RECOVERY
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_AUTHENTICATING_WITH_F8E
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_AWAITING_AUTH_CHALLENGE
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.signChallenge
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle.Back
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AuthenticatingWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingAppSignedAuthChallengeData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingHardwareProofOfPossessionAndKeysData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingHwKeysData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingPushNotificationPermissionData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.DisplayingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToAuthenticateWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToCancelConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToInitiateAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToInitiateLostAppWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.InitiatingAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.InitiatingLostAppRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.ListingKeysetsFromF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.VerifyingNotificationCommsData
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
  val fullAccountConfig: FullAccountConfig,
  val initiatingLostAppRecoveryData: InitiatingLostAppRecoveryData,
)

class InitiatingLostAppRecoveryUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val enableNotificationsUiStateMachine: EnableNotificationsUiStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
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
                isHardwareFake = props.fullAccountConfig.isHardwareFake,
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

      // TODO(W-3273): Drop in proper copy and screen for Listing Keysets screen
      is ListingKeysetsFromF8eData ->
        LoadingBodyModel(
          message = "Retrieving account data...",
          id = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_LISTING_KEYSETS,
          onBack = recoveryData.rollback,
          eventTrackerShouldTrack = false
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
            isHardwareFake = props.fullAccountConfig.isHardwareFake,
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
                commands.signAccessToken(session, recoveryData.authTokens.accessToken)
              )
              val spendingKey = commands.getNextSpendingKey(
                session,
                recoveryData.existingHwSpendingKeys,
                recoveryData.network
              )

              // Sign the new app global auth key with the hardware auth key.
              val appGlobalAuthKeyHwSignature = commands
                .signChallenge(session, recoveryData.newAppGlobalAuthKey.value)
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
            isHardwareFake = props.fullAccountConfig.isHardwareFake,
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
              recoveryNotificationVerificationData = recoveryData.data,
              lostFactor = recoveryData.lostFactor
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
      eventTrackerScreenId = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_ERROR
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
      eventTrackerScreenId = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR
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

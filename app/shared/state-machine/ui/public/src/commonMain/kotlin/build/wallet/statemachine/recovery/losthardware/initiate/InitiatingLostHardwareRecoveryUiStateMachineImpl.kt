package build.wallet.statemachine.recovery.losthardware.initiate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext.HW_RECOVERY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
import build.wallet.analytics.v1.Action.ACTION_APP_HW_RECOVERY_STARTED
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingHardwareProofOfPossessionKeyData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.DisplayingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedBuildingKeyCrossData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedInitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedToCancelConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.InitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionModel
import build.wallet.statemachine.recovery.lostapp.initiate.RecoveryConflictModel
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachineImpl.UiState.AskingNewHardwareReadyQuestionState
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachineImpl.UiState.PairingNewWalletState
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachineImpl.UiState.ShowingInstructionsState
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize

class InitiatingLostHardwareRecoveryUiStateMachineImpl(
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
  private val eventTracker: EventTracker,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
) : InitiatingLostHardwareRecoveryUiStateMachine {
  @Composable
  override fun model(props: InitiatingLostHardwareRecoveryProps): ScreenModel {
    return when (val recoveryData = props.initiatingLostHardwareRecoveryData) {
      is AwaitingNewHardwareData -> {
        val initialScreen =
          when (props.instructionsStyle) {
            InstructionsStyle.Independent -> ShowingInstructionsState
            InstructionsStyle.ContinuingRecovery, InstructionsStyle.ResumedRecoveryAttempt -> AskingNewHardwareReadyQuestionState
          }
        var state: UiState by remember { mutableStateOf(initialScreen) }

        when (state) {
          ShowingInstructionsState ->
            HardwareReplacementInstructionsModel(
              onContinue = {
                state = AskingNewHardwareReadyQuestionState
              },
              onClose = props.onExit
            ).asScreen(props.screenPresentationStyle)

          is AskingNewHardwareReadyQuestionState -> {
            var showingNoDeviceAlert by remember { mutableStateOf(false) }
            NewDeviceReadyQuestionModel(
              showingNoDeviceAlert = showingNoDeviceAlert,
              onNoDeviceAlertDismiss = {
                showingNoDeviceAlert = false
              },
              onBack = {
                when (props.instructionsStyle) {
                  InstructionsStyle.Independent -> {
                    state = ShowingInstructionsState
                  }
                  InstructionsStyle.ContinuingRecovery,
                  InstructionsStyle.ResumedRecoveryAttempt,
                  -> {
                    props.onExit()
                  }
                }
              },
              primaryAction =
                when (props.instructionsStyle) {
                  InstructionsStyle.ResumedRecoveryAttempt ->
                    ButtonModel(
                      text = "I’ve found my old Bitkey device",
                      onClick = Click.standardClick { props.onFoundHardware() },
                      treatment = ButtonModel.Treatment.Tertiary,
                      size = ButtonModel.Size.Footer
                    )
                  else ->
                    ButtonModel(
                      text = "Yes",
                      onClick =
                        Click.standardClick {
                          eventTracker.track(ACTION_APP_HW_RECOVERY_STARTED)
                          state = PairingNewWalletState
                        },
                      size = ButtonModel.Size.Footer
                    )
                },
              secondaryAction =
                when (props.instructionsStyle) {
                  InstructionsStyle.Independent ->
                    ButtonModel(
                      text = "No",
                      onClick =
                        Click.standardClick {
                          showingNoDeviceAlert = true
                        },
                      treatment = ButtonModel.Treatment.Secondary,
                      size = ButtonModel.Size.Footer
                    )
                  InstructionsStyle.ContinuingRecovery ->
                    ButtonModel(
                      text = "No",
                      onClick = Click.standardClick { props.onExit() },
                      treatment = ButtonModel.Treatment.Secondary,
                      size = ButtonModel.Size.Footer
                    )
                  InstructionsStyle.ResumedRecoveryAttempt ->
                    ButtonModel(
                      text = "Yes",
                      onClick =
                        Click.standardClick {
                          eventTracker.track(ACTION_APP_HW_RECOVERY_STARTED)
                          state = PairingNewWalletState
                        },
                      treatment = ButtonModel.Treatment.Primary,
                      size = ButtonModel.Size.Footer
                    )
                },
              presentationStyle = props.screenPresentationStyle,
              showBack =
                when (props.instructionsStyle) {
                  InstructionsStyle.Independent,
                  InstructionsStyle.ResumedRecoveryAttempt,
                  -> true
                  InstructionsStyle.ContinuingRecovery -> false
                },
              backIconModel =
                IconModel(
                  icon =
                    when (props.instructionsStyle) {
                      InstructionsStyle.ResumedRecoveryAttempt -> Icon.SmallIconX
                      else -> Icon.SmallIconArrowLeft
                    },
                  iconSize = IconSize.Accessory,
                  iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
                )
            )
          }

          PairingNewWalletState ->
            pairNewHardwareUiStateMachine.model(
              props =
                PairNewHardwareProps(
                  keyboxConfig = props.keyboxConfig,
                  screenPresentationStyle = props.screenPresentationStyle,
                  onSuccess = { response ->
                    recoveryData.addHardwareKeys(response.sealedCsek, response.keyBundle)
                  },
                  onExit = {
                    state = AskingNewHardwareReadyQuestionState
                  },
                  eventTrackerContext = HW_RECOVERY
                )
            )
        }
      }

      is InitiatingRecoveryWithF8eData -> {
        LoadingBodyModel(
          id = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,
          // TODO(W-3273)
          message = "Preparing New Wallet",
          onBack = recoveryData.rollback
        ).asScreen(props.screenPresentationStyle)
      }

      is FailedBuildingKeyCrossData ->
        InitiateRecoveryErrorScreenModel(
          props = props,
          onRetryClicked = recoveryData.retry,
          onCancelClicked = recoveryData.rollback
        )

      is FailedInitiatingRecoveryWithF8eData ->
        InitiateRecoveryErrorScreenModel(
          props = props,
          onRetryClicked = recoveryData.retry,
          onCancelClicked = recoveryData.rollback
        )

      is VerifyingNotificationCommsData ->
        recoveryNotificationVerificationUiStateMachine.model(
          props =
            RecoveryNotificationVerificationUiProps(
              recoveryNotificationVerificationData = recoveryData.data,
              lostFactor = Hardware
            )
        )

      is CancellingConflictingRecoveryData ->
        LoadingBodyModel(
          "Cancelling Existing Recovery",
          id = LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
        ).asScreen(props.screenPresentationStyle)

      is DisplayingConflictingRecoveryData -> {
        RecoveryConflictModel(
          cancelingRecoveryLostFactor = App,
          onCancelRecovery = recoveryData.onCancelRecovery,
          presentationStyle = props.screenPresentationStyle
        )
      }

      is AwaitingHardwareProofOfPossessionKeyData -> {
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = {
                  recoveryData.onComplete(it)
                }
              ),
            fullAccountId = props.fullAccountId,
            keyboxConfig = props.keyboxConfig,
            screenPresentationStyle = props.screenPresentationStyle,
            onBack = recoveryData.rollback
          )
        )
      }

      is FailedToCancelConflictingRecoveryData ->
        CancelConflictingRecoveryErrorScreenModel(props) {
          recoveryData.onAcknowledge
        }
    }
  }

  private fun InitiateRecoveryErrorScreenModel(
    props: InitiatingLostHardwareRecoveryProps,
    onRetryClicked: () -> Unit,
    onCancelClicked: () -> Unit,
  ) = ErrorFormBodyModel(
    title = "Sorry, we couldn’t initiate recovery process.",
    primaryButton = ButtonDataModel(text = "Retry", onClick = onRetryClicked),
    secondaryButton = ButtonDataModel(text = "Cancel", onClick = onCancelClicked),
    eventTrackerScreenId = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR
  ).asScreen(props.screenPresentationStyle)

  private fun CancelConflictingRecoveryErrorScreenModel(
    props: InitiatingLostHardwareRecoveryProps,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    ErrorFormBodyModel(
      title = "We couldn’t cancel the existing recovery. Please try your recovery again.",
      primaryButton = ButtonDataModel(text = "OK", onClick = onDoneClicked),
      eventTrackerScreenId = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CANCELLATION_ERROR
    ).asScreen(props.screenPresentationStyle)

  private sealed interface UiState {
    data object ShowingInstructionsState : UiState

    data object AskingNewHardwareReadyQuestionState : UiState

    data object PairingNewWalletState : UiState
  }
}

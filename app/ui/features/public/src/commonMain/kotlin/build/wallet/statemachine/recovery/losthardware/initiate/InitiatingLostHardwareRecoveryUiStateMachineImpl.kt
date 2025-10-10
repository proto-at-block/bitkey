package build.wallet.statemachine.recovery.losthardware.initiate

import androidx.compose.runtime.*
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext.HW_RECOVERY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
import build.wallet.analytics.v1.Action.ACTION_APP_HW_RECOVERY_STARTED
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.recovery.CancelDelayNotifyRecoveryError
import build.wallet.recovery.LostHardwareRecoveryService
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps.Request.Ready
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.account.create.full.hardware.PairingContext
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request.HwKeyProof
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionModel
import build.wallet.statemachine.recovery.lostapp.initiate.RecoveryConflictModel
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachineImpl.UiState.*
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class InitiatingLostHardwareRecoveryUiStateMachineImpl(
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
  private val eventTracker: EventTracker,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
  private val lostHardwareRecoveryService: LostHardwareRecoveryService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
) : InitiatingLostHardwareRecoveryUiStateMachine {
  @Composable
  override fun model(props: InitiatingLostHardwareRecoveryProps): ScreenModel {
    var state: UiState by remember { mutableStateOf(GeneratingNewAppKeys) }

    return when (val currentState = state) {
      is AskingNewHardwareReadyQuestionState -> NewDeviceReadyQuestionModel(
        currentState = currentState,
        props = props,
        setState = { state = it }
      )
      is AwaitingHardwareProofOfPossessionState -> proofOfPossessionNfcStateMachine.model(
        ProofOfPossessionNfcProps(
          request = HwKeyProof(
            onSuccess = {
              state = CancellingConflictingRecoveryWithF8eState(
                destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature,
                hwFactorProofOfPossession = it
              )
            }
          ),
          fullAccountId = props.account.accountId,
          appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          actionDescription = "Error getting hardware keyproof",
          screenPresentationStyle = props.screenPresentationStyle,
          hardwareVerification = Required(true),
          onBack = {
            state = GeneratingNewAppKeys
          }
        )
      )
      is CancellingConflictingRecoveryWithF8eState -> {
        LaunchedEffect("cancelling-existing-recovery") {
          lostHardwareRecoveryService.cancelRecoveryWithHwProofOfPossession(
            currentState.hwFactorProofOfPossession
          ).onSuccess {
            state = InitiatingServerRecoveryState(
              destinationAppKeyBundle = currentState.destinationAppKeyBundle,
              destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
              appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
            )
          }
            .onFailure {
              state = if (it is CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError &&
                (it.error as? F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>)?.errorCode
                == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED
              ) {
                VerifyingNotificationCommsState.VerifyingNotificationCommsForCancellationState(
                  destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                  destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                  hwFactorProofOfPossession = currentState.hwFactorProofOfPossession,
                  appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                )
              } else {
                FailedToCancelConflictingRecoveryWithF8EState(
                  cause = it,
                  destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                  destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                  hwFactorProofOfPossession = currentState.hwFactorProofOfPossession,
                  appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                )
              }
            }
        }

        LoadingBodyModel(
          message = "Cancelling Existing Recovery",
          id = LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_LOADING
        ).asScreen(props.screenPresentationStyle)
      }
      is DisplayingConflictingRecoveryState -> RecoveryConflictModel(
        cancelingRecoveryLostFactor = App,
        onCancelRecovery = {
          state = AwaitingHardwareProofOfPossessionState(
            currentState.destinationAppKeyBundle,
            currentState.destinationHardwareKeyBundle,
            currentState.appGlobalAuthKeyHwSignature
          )
        },
        presentationStyle = props.screenPresentationStyle
      )
      is ErrorGeneratingNewAppKeysState -> InitiateRecoveryErrorScreenModel(
        props = props,
        onRetryClicked = {
          state = GeneratingNewAppKeys
        },
        onCancelClicked = props.onExit,
        errorData = ErrorData(
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          actionDescription = "Error generating new app keys",
          cause = currentState.cause
        )
      )
      is FailedInitiatingServerRecoveryState -> InitiateRecoveryErrorScreenModel(
        props = props,
        errorData = ErrorData(
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          cause = currentState.cause,
          actionDescription = "Error initiating recovery with F8e"
        ),
        onRetryClicked = {
          state = InitiatingServerRecoveryState(
            destinationAppKeyBundle = currentState.destinationAppKeyBundle,
            destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
            appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
          )
        },
        onCancelClicked = {
          state = GeneratingNewAppKeys
        }
      )
      is FailedToCancelConflictingRecoveryWithF8EState -> CancelConflictingRecoveryErrorScreenModel(
        props,
        errorData = ErrorData(
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          cause = currentState.cause,
          actionDescription = "Error cancelling conflicting recovery"
        ),
        onDoneClicked = {
          state = GeneratingNewAppKeys
        }
      )
      GeneratingNewAppKeys -> {
        LaunchedEffect("building-key-cross-with-hardware-keys") {
          withMinimumDelay(minimumLoadingDuration.value) {
            lostHardwareRecoveryService.generateNewAppKeys()
          }.onSuccess {
            state = when (props.instructionsStyle) {
              InstructionsStyle.Independent -> ShowingInstructionsState(it)
              InstructionsStyle.ContinuingRecovery, InstructionsStyle.ResumedRecoveryAttempt -> AskingNewHardwareReadyQuestionState(it)
            }
          }
            .onFailure {
              state = ErrorGeneratingNewAppKeysState(it)
            }
        }

        LoadingBodyModel(
          id = null,
          onBack = props.onExit
        ).asScreen(props.screenPresentationStyle)
      }
      is InitiatingServerRecoveryState -> {
        LaunchedEffect("initiating-lost-hardware-server-recovery") {
          lostHardwareRecoveryService.initiate(
            destinationAppKeyBundle = currentState.destinationAppKeyBundle,
            destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
            appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
          )
            .onFailure {
              state = when (it) {
                is CommsVerificationRequiredError ->
                  VerifyingNotificationCommsState.VerifyingNotificationCommsForInitiationState(
                    destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                  )
                is RecoveryAlreadyExistsError ->
                  DisplayingConflictingRecoveryState(
                    destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                  )
                is OtherError ->
                  FailedInitiatingServerRecoveryState(
                    cause = it,
                    destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                  )
              }
            }
        }

        LoadingBodyModel(
          id = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY,
          // TODO(W-3273)
          message = "Preparing New Wallet",
          onBack = {
            state = GeneratingNewAppKeys
          }
        ).asScreen(props.screenPresentationStyle)
      }
      is PairingNewWalletState -> pairNewHardwareUiStateMachine.model(
        props = PairNewHardwareProps(
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          request = Ready(
            appGlobalAuthPublicKey = currentState.newAppKeys.authKey,
            onSuccess = { response ->
              state = InitiatingServerRecoveryState(
                destinationAppKeyBundle = currentState.newAppKeys,
                destinationHardwareKeyBundle = response.keyBundle,
                appGlobalAuthKeyHwSignature = response.appGlobalAuthKeyHwSignature
              )
            }
          ),
          screenPresentationStyle = props.screenPresentationStyle,
          onExit = {
            state = AskingNewHardwareReadyQuestionState(currentState.newAppKeys)
          },
          eventTrackerContext = HW_RECOVERY,
          pairingContext = PairingContext.LostHardware
        )
      )
      is ShowingInstructionsState -> HardwareReplacementInstructionsModel(
        onContinue = {
          state = AskingNewHardwareReadyQuestionState(currentState.newAppKeys)
        },
        onClose = props.onExit
      ).asScreen(props.screenPresentationStyle)
      is VerifyingFoundHardwareState -> proofOfPossessionNfcStateMachine.model(
        ProofOfPossessionNfcProps(
          request = HwKeyProof(
            onSuccess = { currentState.onSuccess() }
          ),
          fullAccountId = props.account.accountId,
          appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          actionDescription = "Error getting hardware keyproof",
          screenPresentationStyle = props.screenPresentationStyle,
          hardwareVerification = Required(),
          onBack = currentState.onBack
        )
      )
      is VerifyingNotificationCommsState -> recoveryNotificationVerificationUiStateMachine.model(
        props = RecoveryNotificationVerificationUiProps(
          fullAccountId = props.account.accountId,
          localLostFactor = Hardware,
          segment = RecoverySegment.DelayAndNotify.LostHardware.Initiation,
          actionDescription = "Error verifying notification comms for contested recovery",
          hwFactorProofOfPossession = null,
          onRollback = {
            state = GeneratingNewAppKeys
          },
          onComplete = {
            state = when (currentState) {
              is VerifyingNotificationCommsState.VerifyingNotificationCommsForCancellationState -> {
                CancellingConflictingRecoveryWithF8eState(
                  destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                  destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                  hwFactorProofOfPossession = currentState.hwFactorProofOfPossession,
                  appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                )
              }

              is VerifyingNotificationCommsState.VerifyingNotificationCommsForInitiationState -> {
                InitiatingServerRecoveryState(
                  destinationAppKeyBundle = currentState.destinationAppKeyBundle,
                  destinationHardwareKeyBundle = currentState.destinationHardwareKeyBundle,
                  appGlobalAuthKeyHwSignature = currentState.appGlobalAuthKeyHwSignature
                )
              }
            }
          }
        )
      )
    }
  }

  private fun NewDeviceReadyQuestionModel(
    currentState: AskingNewHardwareReadyQuestionState,
    props: InitiatingLostHardwareRecoveryProps,
    setState: (UiState) -> Unit,
  ) = NewDeviceReadyQuestionModel(
    showingNoDeviceAlert = currentState.showingNoDeviceAlert,
    onNoDeviceAlertDismiss = {
      setState(currentState.copy(showingNoDeviceAlert = false))
    },
    onBack = {
      when (props.instructionsStyle) {
        InstructionsStyle.Independent -> {
          setState(ShowingInstructionsState(currentState.newAppKeys))
        }
        InstructionsStyle.ContinuingRecovery,
        InstructionsStyle.ResumedRecoveryAttempt,
        -> {
          props.onExit()
        }
      }
    },
    primaryAction = when (props.instructionsStyle) {
      InstructionsStyle.ResumedRecoveryAttempt ->
        ButtonModel(
          text = "I’ve found my old Bitkey device",
          onClick = StandardClick {
            setState(
              VerifyingFoundHardwareState(
                onSuccess = { props.onFoundHardware() },
                onBack = { setState(AskingNewHardwareReadyQuestionState(currentState.newAppKeys)) }
              )
            )
          },
          treatment = ButtonModel.Treatment.Tertiary,
          size = ButtonModel.Size.Footer
        )
      else ->
        ButtonModel(
          text = "Yes",
          onClick = StandardClick {
            eventTracker.track(ACTION_APP_HW_RECOVERY_STARTED)
            setState(PairingNewWalletState(currentState.newAppKeys))
          },
          size = ButtonModel.Size.Footer
        )
    },
    secondaryAction = when (props.instructionsStyle) {
      InstructionsStyle.Independent -> ButtonModel(
        text = "No",
        onClick = StandardClick {
          setState(currentState.copy(showingNoDeviceAlert = true))
        },
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer
      )
      InstructionsStyle.ContinuingRecovery -> ButtonModel(
        text = "No",
        onClick = StandardClick { props.onExit() },
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer
      )
      InstructionsStyle.ResumedRecoveryAttempt -> ButtonModel(
        text = "Yes",
        onClick = StandardClick {
          eventTracker.track(ACTION_APP_HW_RECOVERY_STARTED)
          setState(PairingNewWalletState(currentState.newAppKeys))
        },
        treatment = ButtonModel.Treatment.Primary,
        size = ButtonModel.Size.Footer
      )
    },
    presentationStyle = props.screenPresentationStyle,
    showBack = when (props.instructionsStyle) {
      InstructionsStyle.Independent,
      InstructionsStyle.ResumedRecoveryAttempt,
      -> true
      InstructionsStyle.ContinuingRecovery -> false
    },
    backIconModel = IconModel(
      icon = when (props.instructionsStyle) {
        InstructionsStyle.ResumedRecoveryAttempt -> Icon.SmallIconX
        else -> Icon.SmallIconArrowLeft
      },
      iconSize = IconSize.Accessory,
      iconBackgroundType = Circle(circleSize = IconSize.Regular)
    )
  )

  private fun InitiateRecoveryErrorScreenModel(
    props: InitiatingLostHardwareRecoveryProps,
    errorData: ErrorData,
    onRetryClicked: () -> Unit,
    onCancelClicked: () -> Unit,
  ) = ErrorFormBodyModel(
    title = "Unable to initiate recovery",
    primaryButton = ButtonDataModel(text = "Retry", onClick = onRetryClicked),
    secondaryButton = ButtonDataModel(text = "Cancel", onClick = onCancelClicked),
    eventTrackerScreenId = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR,
    errorData = errorData
  ).asScreen(props.screenPresentationStyle)

  private fun CancelConflictingRecoveryErrorScreenModel(
    props: InitiatingLostHardwareRecoveryProps,
    errorData: ErrorData,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    ErrorFormBodyModel(
      title = "We couldn’t cancel the existing recovery. Please try your recovery again.",
      errorData = errorData,
      primaryButton = ButtonDataModel(text = "OK", onClick = onDoneClicked),
      eventTrackerScreenId = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CANCELLATION_ERROR
    ).asScreen(props.screenPresentationStyle)

  private sealed interface UiState {
    data class ShowingInstructionsState(
      val newAppKeys: AppKeyBundle,
    ) : UiState

    data class AskingNewHardwareReadyQuestionState(
      val newAppKeys: AppKeyBundle,
      val showingNoDeviceAlert: Boolean = false,
    ) : UiState

    data class PairingNewWalletState(
      val newAppKeys: AppKeyBundle,
    ) : UiState

    data class VerifyingFoundHardwareState(
      val onSuccess: () -> Unit,
      val onBack: () -> Unit,
    ) : UiState

    data object GeneratingNewAppKeys : UiState

    data class ErrorGeneratingNewAppKeysState(
      val cause: Throwable,
    ) : UiState

    data class InitiatingServerRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState

    data class FailedInitiatingServerRecoveryState(
      val cause: Throwable,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState

    sealed interface VerifyingNotificationCommsState : UiState {
      val destinationAppKeyBundle: AppKeyBundle
      val destinationHardwareKeyBundle: HwKeyBundle
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature

      data class VerifyingNotificationCommsForInitiationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      ) : VerifyingNotificationCommsState

      data class VerifyingNotificationCommsForCancellationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
        val hwFactorProofOfPossession: HwFactorProofOfPossession,
      ) : VerifyingNotificationCommsState
    }

    data class DisplayingConflictingRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState

    data class CancellingConflictingRecoveryWithF8eState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState

    data class FailedToCancelConflictingRecoveryWithF8EState(
      val cause: Throwable,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState

    data class AwaitingHardwareProofOfPossessionState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : UiState
  }
}

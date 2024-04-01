package build.wallet.statemachine.recovery.conflict

import androidx.compose.runtime.Composable
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.ModalFullScreen
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryFailedData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.VerifyingNotificationCommsData
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.conflict.model.CancelingSomeoneElsesRecoveryFailedSheetModel
import build.wallet.statemachine.recovery.conflict.model.ShowingSomeoneElseIsRecoveringBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine

class SomeoneElseIsRecoveringUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
) : SomeoneElseIsRecoveringUiStateMachine {
  @Composable
  override fun model(props: SomeoneElseIsRecoveringUiProps): ScreenModel {
    return when (props.data) {
      is ShowingSomeoneElseIsRecoveringData ->
        ScreenModel(
          body =
            ShowingSomeoneElseIsRecoveringBodyModel(
              cancelingRecoveryLostFactor = props.data.cancelingRecoveryLostFactor,
              isLoading = false,
              onCancelRecovery = props.data.onCancelRecoveryConflict
            ),
          presentationStyle = Modal
        )

      is CancelingSomeoneElsesRecoveryData ->
        ScreenModel(
          body =
            ShowingSomeoneElseIsRecoveringBodyModel(
              cancelingRecoveryLostFactor = props.data.cancelingRecoveryLostFactor,
              isLoading = true,
              onCancelRecovery = {}
            ),
          presentationStyle = Modal
        )

      is CancelingSomeoneElsesRecoveryFailedData ->
        ScreenModel(
          body =
            ShowingSomeoneElseIsRecoveringBodyModel(
              cancelingRecoveryLostFactor = props.data.cancelingRecoveryLostFactor,
              isLoading = false,
              onCancelRecovery = {}
            ),
          presentationStyle = Modal,
          bottomSheetModel =
            CancelingSomeoneElsesRecoveryFailedSheetModel(
              errorData = ErrorData(
                segment = when (props.data.cancelingRecoveryLostFactor) {
                  App -> RecoverySegment.DelayAndNotify.LostApp.Cancellation
                  Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Cancellation
                },
                actionDescription = "Cancelling someone else's recovery",
                cause = props.data.error
              ),
              onClose = props.data.rollback,
              onRetry = props.data.retry
            )
        )
      is AwaitingHardwareProofOfPossessionData ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = {
                  props.data.onComplete(it)
                }
              ),
            fullAccountId = props.fullAccountId,
            fullAccountConfig = props.fullAccountConfig,
            screenPresentationStyle = ModalFullScreen,
            onBack = props.data.rollback
          )
        )

      is VerifyingNotificationCommsData ->
        recoveryNotificationVerificationUiStateMachine.model(
          props =
            RecoveryNotificationVerificationUiProps(
              recoveryNotificationVerificationData = props.data.data,
              lostFactor = App
            )
        )
    }
  }
}

package build.wallet.statemachine.recovery.conflict

import androidx.compose.runtime.*
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.RecoveryConflictService
import build.wallet.recovery.RecoveryConflictServiceError.CommsVerificationRequired
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachineImpl.State.*
import build.wallet.statemachine.recovery.conflict.model.CancelingSomeoneElsesRecoveryFailedSheetModel
import build.wallet.statemachine.recovery.conflict.model.ShowingSomeoneElseIsRecoveringBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import com.github.michaelbull.result.fold

@BitkeyInject(ActivityScope::class)
class SomeoneElseIsRecoveringUiStateMachineImpl(
  private val recoveryConflictService: RecoveryConflictService,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
) : SomeoneElseIsRecoveringUiStateMachine {
  @Composable
  override fun model(props: SomeoneElseIsRecoveringUiProps): ScreenModel {
    var state: State by remember(props.fullAccountId, props.cancelingRecoveryLostFactor) {
      mutableStateOf(ShowingSomeoneElseIsRecovering)
    }

    return when (val currentState = state) {
      ShowingSomeoneElseIsRecovering -> ScreenModel(
        body = ShowingSomeoneElseIsRecoveringBodyModel(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          isLoading = false,
          // This state machine is only reachable when a FullAccount is active.
          // The service builds an app-signed CancelConflictingRecovery action
          // proof for W3 accounts (no hardware tap needed).
          onCancelRecovery = { state = CancelingSomeoneElsesRecovery }
        ),
        presentationStyle = Modal
      )

      CancelingSomeoneElsesRecovery -> {
        LaunchedEffect("canceling-recovery") {
          recoveryConflictService.cancelRecoveryConflict(
            fullAccountId = props.fullAccountId
          ).fold(
            success = {},
            failure = {
              state = when (it) {
                is CommsVerificationRequired -> VerifyingNotificationComms
                else -> CancelingSomeoneElsesRecoveryFailed(it)
              }
            }
          )
        }
        ScreenModel(
          body = ShowingSomeoneElseIsRecoveringBodyModel(
            cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
            isLoading = true,
            onCancelRecovery = {}
          ),
          presentationStyle = Modal
        )
      }

      is CancelingSomeoneElsesRecoveryFailed -> ScreenModel(
        body = ShowingSomeoneElseIsRecoveringBodyModel(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          isLoading = false,
          onCancelRecovery = {}
        ),
        presentationStyle = Modal,
        bottomSheetModel = CancelingSomeoneElsesRecoveryFailedSheetModel(
          errorData = ErrorData(
            segment = when (props.cancelingRecoveryLostFactor) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Cancellation
              Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Cancellation
            },
            actionDescription = "Cancelling someone else's recovery",
            cause = currentState.cause
          ),
          onClose = { state = ShowingSomeoneElseIsRecovering },
          onRetry = { state = CancelingSomeoneElsesRecovery }
        )
      )

      VerifyingNotificationComms -> recoveryNotificationVerificationUiStateMachine.model(
        props = RecoveryNotificationVerificationUiProps(
          fullAccountId = props.fullAccountId,
          localLostFactor = null,
          onRollback = { state = ShowingSomeoneElseIsRecovering },
          onComplete = { state = CancelingSomeoneElsesRecovery }
        )
      )
    }
  }

  private sealed interface State {
    data object ShowingSomeoneElseIsRecovering : State

    data object CancelingSomeoneElsesRecovery : State

    data class CancelingSomeoneElsesRecoveryFailed(
      val cause: Error,
    ) : State

    data object VerifyingNotificationComms : State
  }
}

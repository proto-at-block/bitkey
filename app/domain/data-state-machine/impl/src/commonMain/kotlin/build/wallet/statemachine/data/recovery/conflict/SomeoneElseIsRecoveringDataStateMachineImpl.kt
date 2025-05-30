package build.wallet.statemachine.data.recovery.conflict

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.f8e.error.F8eError.SpecificClientError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.*
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachineImpl.State.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(AppScope::class)
class SomeoneElseIsRecoveringDataStateMachineImpl(
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoveryStatusService: RecoveryStatusService,
  private val accountConfigService: AccountConfigService,
) : SomeoneElseIsRecoveringDataStateMachine {
  @Composable
  override fun model(props: SomeoneElseIsRecoveringDataProps): SomeoneElseIsRecoveringData {
    var state: State by remember {
      mutableStateOf(ShowingSomeoneElseIsRecoveringDataState)
    }

    return when (val dataState = state) {
      is ShowingSomeoneElseIsRecoveringDataState -> {
        ShowingSomeoneElseIsRecoveringData(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          onCancelRecoveryConflict = {
            // We don't require HW PoP on cancellations from LostApp, because in most cases,
            // the customer won't have the HW.
            state =
              when (props.cancelingRecoveryLostFactor) {
                Hardware -> {
                  AwaitingHardwareProofOfPossessionState(
                    onGainedProofOfPossession = {
                      CancelingSomeoneElsesRecoveryDataState(
                        hwFactorProofOfPossession = it
                      )
                    }
                  )
                }

                App -> {
                  CancelingSomeoneElsesRecoveryDataState(null)
                }
              }
          }
        )
      }

      is CancelingSomeoneElsesRecoveryDataState -> {
        LaunchedEffect("canceling-recovery") {
          val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
          cancelDelayNotifyRecoveryF8eClient.cancel(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = props.fullAccountId,
            hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
          ).onSuccess {
            recoveryStatusService.clear()
          }.onFailure {
            val f8eError = it as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
            state = when {
              f8eError != null && f8eError.errorCode == COMMS_VERIFICATION_REQUIRED -> {
                VerifyingNotificationCommsState(hwFactorProofOfPossession = dataState.hwFactorProofOfPossession)
              }
              else -> {
                CancelingSomeoneElsesRecoveryFailedDataState(
                  cause = it.error,
                  hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                )
              }
            }
          }
        }
        CancelingSomeoneElsesRecoveryData(props.cancelingRecoveryLostFactor)
      }

      is CancelingSomeoneElsesRecoveryFailedDataState -> {
        CancelingSomeoneElsesRecoveryFailedData(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          rollback = {
            state = ShowingSomeoneElseIsRecoveringDataState
          },
          error = dataState.cause,
          retry = {
            state =
              CancelingSomeoneElsesRecoveryDataState(
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
          }
        )
      }

      is AwaitingHardwareProofOfPossessionState ->
        AwaitingHardwareProofOfPossessionData(
          onComplete = {
            state = CancelingSomeoneElsesRecoveryDataState(it)
          },
          rollback = { state = ShowingSomeoneElseIsRecoveringDataState }
        )

      is VerifyingNotificationCommsState -> {
        VerifyingNotificationCommsData(
          fullAccountId = props.fullAccountId,
          onRollback = {
            state = ShowingSomeoneElseIsRecoveringDataState
          },
          onComplete = {
            // Once we've verified we can try to re-initiate with F8e
            state =
              CancelingSomeoneElsesRecoveryDataState(
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
          },
          hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
        )
      }
    }
  }

  private sealed interface State {
    data object ShowingSomeoneElseIsRecoveringDataState : State

    data class AwaitingHardwareProofOfPossessionState(
      val onGainedProofOfPossession: (HwFactorProofOfPossession) -> Unit,
    ) : State

    data class CancelingSomeoneElsesRecoveryDataState(
      val hwFactorProofOfPossession: HwFactorProofOfPossession?,
    ) : State

    data class CancelingSomeoneElsesRecoveryFailedDataState(
      val cause: Error,
      val hwFactorProofOfPossession: HwFactorProofOfPossession?,
    ) : State

    data class VerifyingNotificationCommsState(
      val hwFactorProofOfPossession: HwFactorProofOfPossession?,
    ) : State
  }
}

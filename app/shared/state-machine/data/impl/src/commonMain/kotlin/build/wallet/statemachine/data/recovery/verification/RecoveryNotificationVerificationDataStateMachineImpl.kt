package build.wallet.statemachine.data.recovery.verification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.notifications.NotificationTouchpointService
import build.wallet.f8e.recovery.RecoveryNotificationVerificationService
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.ChoosingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.EnteringVerificationCodeData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.ChoosingNotificationTouchpointDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.EnteringVerificationCodeDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.LoadingNotificationTouchpointDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.LoadingNotificationTouchpointFailureDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.SendingNotificationTouchpointToServerDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.SendingNotificationTouchpointToServerFailureDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.SendingVerificationCodeToServerDataState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachineImpl.State.SendingVerificationCodeToServerFailureDataState
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class RecoveryNotificationVerificationDataStateMachineImpl(
  private val notificationTouchpointService: NotificationTouchpointService,
  private val recoveryNotificationVerificationService: RecoveryNotificationVerificationService,
) : RecoveryNotificationVerificationDataStateMachine {
  @Composable
  override fun model(
    props: RecoveryNotificationVerificationDataProps,
  ): RecoveryNotificationVerificationData {
    var dataState: State by remember { mutableStateOf(LoadingNotificationTouchpointDataState) }

    return when (val state = dataState) {
      is LoadingNotificationTouchpointDataState -> {
        LaunchedEffect("load-notifications") {
          notificationTouchpointService.getTouchpoints(
            f8eEnvironment = props.f8eEnvironment,
            fullAccountId = props.fullAccountId
          )
            .onFailure { error ->
              dataState = LoadingNotificationTouchpointFailureDataState(error)
            }
            .onSuccess { touchpoints ->
              // TODO (W-3872): Handle empty list
              dataState = ChoosingNotificationTouchpointDataState(touchpoints)
            }
        }
        LoadingNotificationTouchpointData
      }

      is LoadingNotificationTouchpointFailureDataState ->
        LoadingNotificationTouchpointFailureData(
          rollback = props.onRollback,
          retry = {
            dataState = LoadingNotificationTouchpointDataState
          },
          error = state.error
        )

      is ChoosingNotificationTouchpointDataState -> {
        val smsTouchpoint =
          state.availableTouchpoints.firstOrNull {
            it is NotificationTouchpoint.PhoneNumberTouchpoint
          }
        val emailTouchpoint =
          state.availableTouchpoints.firstOrNull {
            it is NotificationTouchpoint.EmailTouchpoint
          }

        if (smsTouchpoint == null && emailTouchpoint == null) {
          // TODO (W-3872): Handle empty list of touchpoints
          Unit
        }

        ChoosingNotificationTouchpointData(
          rollback = props.onRollback,
          onSmsClick =
            smsTouchpoint?.let {
              {
                dataState =
                  SendingNotificationTouchpointToServerDataState(
                    availableTouchpoints = state.availableTouchpoints,
                    touchpoint = it
                  )
              }
            },
          onEmailClick =
            emailTouchpoint?.let {
              {
                dataState =
                  SendingNotificationTouchpointToServerDataState(
                    availableTouchpoints = state.availableTouchpoints,
                    touchpoint = it
                  )
              }
            }
        )
      }

      is SendingNotificationTouchpointToServerDataState -> {
        LaunchedEffect("send-touchpoint-to-server") {
          recoveryNotificationVerificationService.sendVerificationCodeToTouchpoint(
            f8eEnvironment = props.f8eEnvironment,
            fullAccountId = props.fullAccountId,
            touchpoint = state.touchpoint,
            hardwareProofOfPossession = props.hwFactorProofOfPossession
          )
            .onFailure { error ->
              dataState =
                SendingNotificationTouchpointToServerFailureDataState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = state.touchpoint,
                  error = error
                )
            }
            .onSuccess {
              dataState =
                EnteringVerificationCodeDataState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = state.touchpoint
                )
            }
        }
        SendingNotificationTouchpointToServerData
      }

      is SendingNotificationTouchpointToServerFailureDataState ->
        SendingNotificationTouchpointToServerFailureData(
          rollback = {
            dataState = ChoosingNotificationTouchpointDataState(state.availableTouchpoints)
          },
          retry = {
            dataState =
              SendingNotificationTouchpointToServerDataState(
                availableTouchpoints = state.availableTouchpoints,
                touchpoint = state.touchpoint
              )
          },
          error = state.error
        )

      is EnteringVerificationCodeDataState -> {
        var isResendingCode by remember { mutableStateOf(false) }
        var onSuccess: (() -> Unit)? by remember { mutableStateOf(null) }
        var onFailure: (
          (
            isConnectivityError: Boolean,
          ) -> Unit
        )? by remember { mutableStateOf(null) }

        if (isResendingCode) {
          LaunchedEffect("send-touchpoint-to-server") {
            recoveryNotificationVerificationService.sendVerificationCodeToTouchpoint(
              f8eEnvironment = props.f8eEnvironment,
              fullAccountId = props.fullAccountId,
              touchpoint = state.touchpoint,
              hardwareProofOfPossession = props.hwFactorProofOfPossession
            )
              .onFailure {
                isResendingCode = false
                onFailure?.invoke(it is HttpError.NetworkError)
              }
              .onSuccess {
                isResendingCode = false
                onSuccess?.invoke()
              }
          }
        }

        EnteringVerificationCodeData(
          rollback = {
            dataState = ChoosingNotificationTouchpointDataState(state.availableTouchpoints)
          },
          touchpoint = state.touchpoint,
          onCodeEntered = { verificationCode ->
            dataState =
              SendingVerificationCodeToServerDataState(
                availableTouchpoints = state.availableTouchpoints,
                touchpoint = state.touchpoint,
                verificationCode = verificationCode
              )
          },
          onResendCode = { onSuccessCallback, onFailureCallback ->
            onSuccess = onSuccessCallback
            onFailure = onFailureCallback
            isResendingCode = true
          },
          lostFactor = props.lostFactor
        )
      }

      is SendingVerificationCodeToServerDataState -> {
        LaunchedEffect("send-verification-code-to-server") {
          recoveryNotificationVerificationService.verifyCode(
            f8eEnvironment = props.f8eEnvironment,
            fullAccountId = props.fullAccountId,
            verificationCode = state.verificationCode,
            hardwareProofOfPossession = props.hwFactorProofOfPossession
          )
            .onFailure { error ->
              dataState =
                SendingVerificationCodeToServerFailureDataState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = state.touchpoint,
                  verificationCode = state.verificationCode,
                  error = error
                )
            }
            .onSuccess {
              props.onComplete()
            }
        }
        SendingVerificationCodeToServerData
      }

      is SendingVerificationCodeToServerFailureDataState ->
        SendingVerificationCodeToServerFailureData(
          rollback = {
            val errorIsRecoverable =
              when (state.error) {
                is F8eError.SpecificClientError ->
                  when (state.error.errorCode) {
                    VerifyTouchpointClientErrorCode.CODE_MISMATCH -> true
                    VerifyTouchpointClientErrorCode.CODE_EXPIRED -> false
                  }
                is F8eError.ConnectivityError -> true
                else -> false
              }

            dataState =
              when (errorIsRecoverable) {
                // Go back to code input if the error is recoverable.
                true ->
                  EnteringVerificationCodeDataState(
                    availableTouchpoints = state.availableTouchpoints,
                    touchpoint = state.touchpoint
                  )
                // Otherwise, go all the way back to the choosing state so
                // the customer will get a new code.
                false -> ChoosingNotificationTouchpointDataState(state.availableTouchpoints)
              }
          },
          retry = {
            dataState =
              SendingVerificationCodeToServerDataState(
                availableTouchpoints = state.availableTouchpoints,
                touchpoint = state.touchpoint,
                verificationCode = state.verificationCode
              )
          },
          error = state.error
        )
    }
  }

  private sealed interface State {
    data object LoadingNotificationTouchpointDataState : State

    data class LoadingNotificationTouchpointFailureDataState(
      val error: NetworkingError,
    ) : State

    data class ChoosingNotificationTouchpointDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
    ) : State

    data class SendingNotificationTouchpointToServerDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
      val touchpoint: NotificationTouchpoint,
    ) : State

    data class SendingNotificationTouchpointToServerFailureDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
      val touchpoint: NotificationTouchpoint,
      val error: NetworkingError,
    ) : State

    data class EnteringVerificationCodeDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
      val touchpoint: NotificationTouchpoint,
    ) : State

    data class SendingVerificationCodeToServerDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
      val touchpoint: NotificationTouchpoint,
      val verificationCode: String,
    ) : State

    data class SendingVerificationCodeToServerFailureDataState(
      val availableTouchpoints: List<NotificationTouchpoint>,
      val touchpoint: NotificationTouchpoint,
      val verificationCode: String,
      val error: F8eError<VerifyTouchpointClientErrorCode>,
    ) : State
  }
}

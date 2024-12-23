package build.wallet.statemachine.recovery.verification

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.CANCELLING_SOMEONE_ELSE_IS_RECOVERING_COMMS_VERIFICATION_ENTRY
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.notifications.NotificationTouchpointService
import build.wallet.recovery.getEventId
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.input.VerificationCodeInputProps
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachine
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiState.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class RecoveryNotificationVerificationUiStateMachineImpl(
  private val verificationCodeInputStateMachine: VerificationCodeInputStateMachine,
  private val notificationTouchpointService: NotificationTouchpointService,
) : RecoveryNotificationVerificationUiStateMachine {
  @Composable
  override fun model(props: RecoveryNotificationVerificationUiProps): ScreenModel {
    var uiState: RecoveryNotificationVerificationUiState by remember {
      mutableStateOf(LoadingNotificationTouchpointUiState)
    }

    return when (val state = uiState) {
      is LoadingNotificationTouchpointUiState -> {
        LaunchedEffect("load-notifications") {
          notificationTouchpointService.syncNotificationTouchpoints(
            accountId = props.fullAccountId,
            f8eEnvironment = props.f8eEnvironment
          )
            .onFailure { error ->
              uiState = LoadingNotificationTouchpointFailureUiState(
                error = error
              )
            }
            .onSuccess { touchpoints ->
              // TODO (W-3872): Handle empty list
              uiState = ChoosingNotificationTouchpointUiState(touchpoints)
            }
        }

        LoadingBodyModel(id = null)
          .asModalScreen()
      }

      is LoadingNotificationTouchpointFailureUiState ->
        LoadingNotificationTouchpointFailureModel(
          props = props,
          onRetry = { uiState = LoadingNotificationTouchpointUiState },
          error = state.error
        )
          .asModalScreen()

      is ChoosingNotificationTouchpointUiState -> {
        val smsTouchpoint =
          state.availableTouchpoints.firstOrNull {
            it is PhoneNumberTouchpoint
          }
        val emailTouchpoint =
          state.availableTouchpoints.firstOrNull {
            it is EmailTouchpoint
          }

        if (smsTouchpoint == null && emailTouchpoint == null) {
          // TODO (W-3872): Handle empty list of touchpoints
          Unit
        }

        ChooseRecoveryNotificationVerificationMethodModel(
          onBack = props.onRollback,
          onSmsClick = smsTouchpoint?.let {
            {
              uiState = SendingNotificationTouchpointToServerUiState(
                availableTouchpoints = state.availableTouchpoints,
                touchpoint = it
              )
            }
          },
          onEmailClick = emailTouchpoint?.let {
            {
              uiState =
                SendingNotificationTouchpointToServerUiState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = it
                )
            }
          }
        ).asModalScreen()
      }

      is SendingNotificationTouchpointToServerUiState -> {
        LaunchedEffect("send-touchpoint-to-server") {
          notificationTouchpointService.sendVerificationCodeToTouchpoint(
            f8eEnvironment = props.f8eEnvironment,
            fullAccountId = props.fullAccountId,
            touchpoint = state.touchpoint,
            hwProofOfPossession = props.hwFactorProofOfPossession
          )
            .onFailure { error ->
              uiState =
                SendingNotificationTouchpointToServerFailureUiState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = state.touchpoint,
                  error = error
                )
            }
            .onSuccess {
              uiState =
                EnteringVerificationCodeUiState(
                  availableTouchpoints = state.availableTouchpoints,
                  touchpoint = state.touchpoint
                )
            }
        }

        LoadingBodyModel(id = null)
          .asModalScreen()
      }

      is SendingNotificationTouchpointToServerFailureUiState ->
        SendingNotificationTouchpointToServerFailureModel(
          props = props,
          error = state.error,
          onRetry = {
            uiState = SendingNotificationTouchpointToServerUiState(
              availableTouchpoints = state.availableTouchpoints,
              touchpoint = state.touchpoint
            )
          },
          rollback = { uiState = ChoosingNotificationTouchpointUiState(state.availableTouchpoints) }
        )
          .asModalScreen()

      is EnteringVerificationCodeUiState -> {
        var isResendingCode by remember { mutableStateOf(false) }
        var onSuccess: (() -> Unit)? by remember { mutableStateOf(null) }
        var onFailure: (
          (
            isConnectivityError: Boolean,
          ) -> Unit
        )? by remember { mutableStateOf(null) }

        if (isResendingCode) {
          LaunchedEffect("send-touchpoint-to-server") {
            notificationTouchpointService.sendVerificationCodeToTouchpoint(
              f8eEnvironment = props.f8eEnvironment,
              fullAccountId = props.fullAccountId,
              touchpoint = state.touchpoint,
              hwProofOfPossession = props.hwFactorProofOfPossession
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

        EnteringVerificationCodeModel(
          rollback = {
            uiState = ChoosingNotificationTouchpointUiState(state.availableTouchpoints)
          },
          touchpoint = state.touchpoint,
          onCodeEntered = { verificationCode ->
            uiState =
              SendingVerificationCodeToServerUiState(
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
          lostFactor = props.localLostFactor
        )
      }

      is SendingVerificationCodeToServerUiState -> {
        LaunchedEffect("send-verification-code-to-server") {
          notificationTouchpointService.verifyCode(
            f8eEnvironment = props.f8eEnvironment,
            fullAccountId = props.fullAccountId,
            verificationCode = state.verificationCode,
            hwProofOfPossession = props.hwFactorProofOfPossession
          )
            .onFailure { error ->
              uiState =
                SendingVerificationCodeToServerFailureUiState(
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

        LoadingBodyModel(id = null)
          .asModalScreen()
      }

      is SendingVerificationCodeToServerFailureUiState ->
        SendingVerificationCodeToServerFailureModel(
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

            uiState =
              when (errorIsRecoverable) {
                // Go back to code input if the error is recoverable.
                true ->
                  EnteringVerificationCodeUiState(
                    availableTouchpoints = state.availableTouchpoints,
                    touchpoint = state.touchpoint
                  )
                // Otherwise, go all the way back to the choosing state so
                // the customer will get a new code.
                false -> ChoosingNotificationTouchpointUiState(state.availableTouchpoints)
              }
          },
          retry = {
            uiState =
              SendingVerificationCodeToServerUiState(
                availableTouchpoints = state.availableTouchpoints,
                touchpoint = state.touchpoint,
                verificationCode = state.verificationCode
              )
          },
          error = state.error
        )
          .asModalScreen()
    }
  }

  @Composable
  private fun LoadingNotificationTouchpointFailureModel(
    props: RecoveryNotificationVerificationUiProps,
    onRetry: () -> Unit,
    error: Error,
  ): BodyModel {
    return NetworkErrorFormBodyModelWithOptionalErrorData(
      title = "We couldn’t load verification for recovery",
      isConnectivityError = error is HttpError.NetworkError,
      onRetry = onRetry,
      onBack = props.onRollback,
      errorData = if (props.segment != null && props.actionDescription != null) {
        ErrorData(
          segment = props.segment,
          cause = error,
          actionDescription = props.actionDescription
        )
      } else {
        null
      },
      eventTrackerScreenId = null
    )
  }

  @Composable
  private fun SendingNotificationTouchpointToServerFailureModel(
    props: RecoveryNotificationVerificationUiProps,
    onRetry: () -> Unit,
    rollback: () -> Unit,
    error: Error,
  ): BodyModel {
    return NetworkErrorFormBodyModelWithOptionalErrorData(
      title = "We couldn’t send a verification code",
      isConnectivityError = error is HttpError.NetworkError,
      onRetry = onRetry,
      onBack = rollback,
      errorData = if (props.segment != null && props.actionDescription != null) {
        ErrorData(
          segment = props.segment,
          cause = error,
          actionDescription = props.actionDescription
        )
      } else {
        null
      },
      eventTrackerScreenId = null
    )
  }

  @Composable
  private fun EnteringVerificationCodeModel(
    rollback: () -> Unit,
    touchpoint: NotificationTouchpoint,
    onResendCode: (
      onSuccess: () -> Unit,
      onError: (isConnectivityError: Boolean) -> Unit,
    ) -> Unit,
    onCodeEntered: (verificationCode: String) -> Unit,
    lostFactor: PhysicalFactor?,
  ): ScreenModel {
    var resendCodeCallbacks: ResendCodeCallbacks? by remember { mutableStateOf(null) }

    // Side Effect: Resend if callbacks are present

    // If the [resendCodeCallbacks] have been set on the state, we
    // need to resend the touchpoint to the server in order for
    // the verification code to be resent to the customer.
    resendCodeCallbacks?.let { callbacks ->
      onResendCode(callbacks.onSuccess, callbacks.onError)
    }

    // Return model
    return verificationCodeInputStateMachine.model(
      props =
        VerificationCodeInputProps(
          title =
            when (touchpoint) {
              is PhoneNumberTouchpoint -> "Verify your phone number"
              is EmailTouchpoint -> "Verify your email"
            },
          subtitle =
            when (touchpoint) {
              is PhoneNumberTouchpoint -> "We sent a code to the phone number linked to your device."
              is EmailTouchpoint -> "We sent a code to the email account linked to your device."
            },
          expectedCodeLength = 6,
          notificationTouchpoint = touchpoint,
          onBack = rollback,
          onCodeEntered = onCodeEntered,
          onResendCode = {
            resendCodeCallbacks = it
          },
          skipBottomSheetProvider = null,
          screenId = lostFactor.toScreenId()
        )
    )
  }

  /**
   * Determines the [EventTrackerScreenId] for the local lost factor, if there is one. This ensures
   * continuity in the analytics by making it obvious what type of recovery flow for which
   * touchpoints are being verified. If there isn't a local lost factor, such as when a touchpoint
   * is being verified due canceling a server recovery, we use a default screen id.
   */
  private fun PhysicalFactor?.toScreenId(): EventTrackerScreenId =
    this?.getEventId(
      app = LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY,
      hw = LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
    ) ?: CANCELLING_SOMEONE_ELSE_IS_RECOVERING_COMMS_VERIFICATION_ENTRY

  @Composable
  private fun SendingVerificationCodeToServerFailureModel(
    rollback: () -> Unit,
    retry: () -> Unit,
    error: F8eError<VerifyTouchpointClientErrorCode>,
  ): BodyModel {
    val errorTitle = "We couldn’t verify the entered code"
    return when (error) {
      is F8eError.SpecificClientError ->
        when (error.errorCode) {
          VerifyTouchpointClientErrorCode.CODE_EXPIRED ->
            ErrorFormBodyModel(
              title = errorTitle,
              subline = "Your verification code has expired. Please request a new code.",
              primaryButton =
                ButtonDataModel(
                  text = "Back",
                  onClick = rollback
                ),
              eventTrackerScreenId = null
            )

          VerifyTouchpointClientErrorCode.CODE_MISMATCH ->
            ErrorFormBodyModel(
              title = errorTitle,
              subline = "The verification code was incorrect. Please try again.",
              primaryButton = ButtonDataModel(text = "Back", onClick = rollback),
              eventTrackerScreenId = null
            )
        }

      else -> {
        NetworkErrorFormBodyModel(
          title = errorTitle,
          isConnectivityError = error is F8eError.ConnectivityError,
          onRetry = retry,
          onBack = rollback,
          eventTrackerScreenId = null
        )
      }
    }
  }
}

private sealed interface RecoveryNotificationVerificationUiState {
  data object LoadingNotificationTouchpointUiState : RecoveryNotificationVerificationUiState

  data class LoadingNotificationTouchpointFailureUiState(
    val error: Error,
  ) : RecoveryNotificationVerificationUiState

  data class ChoosingNotificationTouchpointUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
  ) : RecoveryNotificationVerificationUiState

  data class SendingNotificationTouchpointToServerUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
    val touchpoint: NotificationTouchpoint,
  ) : RecoveryNotificationVerificationUiState

  data class SendingNotificationTouchpointToServerFailureUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
    val touchpoint: NotificationTouchpoint,
    val error: Error,
  ) : RecoveryNotificationVerificationUiState

  data class EnteringVerificationCodeUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
    val touchpoint: NotificationTouchpoint,
  ) : RecoveryNotificationVerificationUiState

  data class SendingVerificationCodeToServerUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
    val touchpoint: NotificationTouchpoint,
    val verificationCode: String,
  ) : RecoveryNotificationVerificationUiState

  data class SendingVerificationCodeToServerFailureUiState(
    val availableTouchpoints: List<NotificationTouchpoint>,
    val touchpoint: NotificationTouchpoint,
    val verificationCode: String,
    val error: F8eError<VerifyTouchpointClientErrorCode>,
  ) : RecoveryNotificationVerificationUiState
}

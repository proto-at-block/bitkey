package build.wallet.statemachine.recovery.verification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.recovery.getEventId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModelWithOptionalErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.input.VerificationCodeInputProps
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachine
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.ChoosingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.EnteringVerificationCodeData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerFailureData

class RecoveryNotificationVerificationUiStateMachineImpl(
  private val verificationCodeInputStateMachine: VerificationCodeInputStateMachine,
) : RecoveryNotificationVerificationUiStateMachine {
  @Composable
  override fun model(props: RecoveryNotificationVerificationUiProps): ScreenModel {
    return when (val data = props.recoveryNotificationVerificationData) {
      is LoadingNotificationTouchpointData ->
        LoadingBodyModel(id = null)
          .asModalScreen()

      is LoadingNotificationTouchpointFailureData ->
        LoadingNotificationTouchpointFailureModel(props, data)
          .asModalScreen()

      is ChoosingNotificationTouchpointData ->
        ChooseRecoveryNotificationVerificationMethodModel(
          onBack = data.rollback,
          onSmsClick = data.onSmsClick,
          onEmailClick = data.onEmailClick
        ).asModalScreen()

      is SendingNotificationTouchpointToServerData ->
        LoadingBodyModel(id = null)
          .asModalScreen()

      is SendingNotificationTouchpointToServerFailureData ->
        SendingNotificationTouchpointToServerFailureModel(props, data)
          .asModalScreen()

      is EnteringVerificationCodeData ->
        EnteringVerificationCodeModel(data)

      is SendingVerificationCodeToServerData ->
        LoadingBodyModel(id = null)
          .asModalScreen()

      is SendingVerificationCodeToServerFailureData ->
        SendingVerificationCodeToServerFailureModel(data)
          .asModalScreen()
    }
  }

  @Composable
  private fun LoadingNotificationTouchpointFailureModel(
    props: RecoveryNotificationVerificationUiProps,
    data: LoadingNotificationTouchpointFailureData,
  ): BodyModel {
    return NetworkErrorFormBodyModelWithOptionalErrorData(
      title = "We couldn’t load verification for recovery",
      isConnectivityError = data.error is HttpError.NetworkError,
      onRetry = data.retry,
      onBack = data.rollback,
      errorData = if (props.segment != null && props.actionDescription != null) {
        ErrorData(
          segment = props.segment,
          cause = data.error,
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
    data: SendingNotificationTouchpointToServerFailureData,
  ): BodyModel {
    return NetworkErrorFormBodyModelWithOptionalErrorData(
      title = "We couldn’t send a verification code",
      isConnectivityError = data.error is HttpError.NetworkError,
      onRetry = data.retry,
      onBack = data.rollback,
      errorData = if (props.segment != null && props.actionDescription != null) {
        ErrorData(
          segment = props.segment,
          cause = data.error,
          actionDescription = props.actionDescription
        )
      } else {
        null
      },
      eventTrackerScreenId = null
    )
  }

  @Composable
  private fun EnteringVerificationCodeModel(data: EnteringVerificationCodeData): ScreenModel {
    var resendCodeCallbacks: ResendCodeCallbacks? by remember { mutableStateOf(null) }

    // Side Effect: Resend if callbacks are present

    // If the [resendCodeCallbacks] have been set on the state, we
    // need to resend the touchpoint to the server in order for
    // the verification code to be resent to the customer.
    resendCodeCallbacks?.let { callbacks ->
      data.onResendCode(callbacks.onSuccess, callbacks.onError)
    }

    // Return model
    return verificationCodeInputStateMachine.model(
      props =
        VerificationCodeInputProps(
          title =
            when (data.touchpoint) {
              is PhoneNumberTouchpoint -> "Verify your phone number"
              is EmailTouchpoint -> "Verify your email"
            },
          subtitle =
            when (data.touchpoint) {
              is PhoneNumberTouchpoint -> "We sent a code to the phone number linked to your device."
              is EmailTouchpoint -> "We sent a code to the email account linked to your device."
            },
          expectedCodeLength = 6,
          notificationTouchpoint = data.touchpoint,
          onBack = data.rollback,
          onCodeEntered = data.onCodeEntered,
          onResendCode = {
            resendCodeCallbacks = it
          },
          skipBottomSheetProvider = null,
          screenId =
            data.lostFactor.getEventId(
              LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY,
              LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
            )
        )
    )
  }

  @Composable
  private fun SendingVerificationCodeToServerFailureModel(
    data: SendingVerificationCodeToServerFailureData,
  ): BodyModel {
    val errorTitle = "We couldn’t verify the entered code"
    return when (val error = data.error) {
      is F8eError.SpecificClientError ->
        when (error.errorCode) {
          VerifyTouchpointClientErrorCode.CODE_EXPIRED ->
            ErrorFormBodyModel(
              title = errorTitle,
              subline = "Your verification code has expired. Please request a new code.",
              primaryButton =
                ButtonDataModel(
                  text = "Back",
                  onClick = data.rollback
                ),
              eventTrackerScreenId = null
            )

          VerifyTouchpointClientErrorCode.CODE_MISMATCH ->
            ErrorFormBodyModel(
              title = errorTitle,
              subline = "The verification code was incorrect. Please try again.",
              primaryButton = ButtonDataModel(text = "Back", onClick = data.rollback),
              eventTrackerScreenId = null
            )
        }

      else -> {
        NetworkErrorFormBodyModel(
          title = errorTitle,
          isConnectivityError = data.error is F8eError.ConnectivityError,
          onRetry = data.retry,
          onBack = data.rollback,
          eventTrackerScreenId = null
        )
      }
    }
  }
}

package build.wallet.statemachine.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.notifications.NotificationTouchpointService
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.input.EmailInputUiProps
import build.wallet.statemachine.core.input.EmailInputUiStateMachine
import build.wallet.statemachine.core.input.PhoneNumberInputUiProps
import build.wallet.statemachine.core.input.PhoneNumberInputUiStateMachine
import build.wallet.statemachine.core.input.VerificationCodeInputProps
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachine
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Onboarding
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.EnteringTouchpointUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.EnteringVerificationCodeUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.SendingActivationToServerFailureUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.SendingActivationToServerSuccessUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.SendingActivationToServerUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.SendingVerificationCodeToServerFailureUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.SendingVerificationCodeToServerUiState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.VerifyingProofOfHwPossessionUiState
import build.wallet.statemachine.notifications.NotificationTouchpointSubmissionState.None
import build.wallet.statemachine.notifications.NotificationTouchpointSubmissionState.SendingTouchpointToServer
import build.wallet.time.Delayer
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

class NotificationTouchpointInputAndVerificationUiStateMachineImpl(
  private val delayer: Delayer,
  private val emailInputUiStateMachine: EmailInputUiStateMachine,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val notificationTouchpointService: NotificationTouchpointService,
  private val phoneNumberInputUiStateMachine: PhoneNumberInputUiStateMachine,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val verificationCodeInputStateMachine: VerificationCodeInputStateMachine,
  private val uiErrorHintSubmitter: UiErrorHintSubmitter,
) : NotificationTouchpointInputAndVerificationUiStateMachine {
  @Composable
  override fun model(props: NotificationTouchpointInputAndVerificationProps): ScreenModel {
    var uiState: NotificationTouchpointInputAndVerificationUiState by remember {
      mutableStateOf(EnteringTouchpointUiState(touchpointPrefill = null))
    }

    return when (val state = uiState) {
      is EnteringTouchpointUiState ->
        EnteringTouchpointModel(
          props = props,
          state = state,
          onSubmissionToServerSuccess = { phoneNumber ->
            if (props.touchpointType == PhoneNumber) {
              uiErrorHintSubmitter.phoneNone()
            }
            uiState =
              EnteringVerificationCodeUiState(
                touchpointToVerify = phoneNumber
              )
          }
        )

      is EnteringVerificationCodeUiState ->
        EnteringVerificationCodeModel(
          props = props,
          state = state,
          onBack = {
            uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToVerify)
          },
          onCodeEntered = { verificationCode ->
            uiState =
              SendingVerificationCodeToServerUiState(
                touchpointToVerify = state.touchpointToVerify,
                verificationCode = verificationCode
              )
          }
        )

      is SendingVerificationCodeToServerUiState ->
        SendingVerificationCodeToServerModel(
          props = props,
          state = state,
          setState = { newState ->
            uiState = newState
          }
        ).asModalScreen()

      is SendingVerificationCodeToServerFailureUiState ->
        SendingVerificationCodeToServerFailureModel(
          props = props,
          state = state,
          onBackToTouchpointInput = {
            uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToVerify)
          },
          onBackToCodeInput = {
            uiState = EnteringVerificationCodeUiState(state.touchpointToVerify)
          }
        ).asModalScreen()

      is ActivationApprovalInstructionsUiState ->
        NotificationOperationApprovalInstructionsFormScreenModel(
          onExit = props.onClose,
          operationDescriptiton = operationDescription(state.touchpointToActivate.formattedDisplayValue),
          isApproveButtonLoading = false,
          errorBottomSheetState = ErrorBottomSheetState.Hidden,
          onApprove = {
            uiState =
              VerifyingProofOfHwPossessionUiState(
                touchpointToActivate = state.touchpointToActivate
              )
          }
        )

      is VerifyingProofOfHwPossessionUiState ->
        VerifyingProofOfHwPossessionModel(
          props = props,
          state = state,
          goToActivationInstructions = {
            uiState =
              ActivationApprovalInstructionsUiState(
                touchpointToActivate = state.touchpointToActivate,
                errorBottomSheetState = ErrorBottomSheetState.Hidden
              )
          }
        ) { hwFactorProofOfPossession ->
          uiState =
            SendingActivationToServerUiState(
              touchpointToActivate = state.touchpointToActivate,
              hwFactorProofOfPossession = hwFactorProofOfPossession
            )
        }

      is SendingActivationToServerUiState ->
        SendingActivationToServerModel(
          props = props,
          state = state,
          onSuccess = {
            uiState =
              SendingActivationToServerSuccessUiState(
                requiredHwProofOfPossession = state.hwFactorProofOfPossession != null
              )
          },
          onFailure = { error ->
            uiState =
              SendingActivationToServerFailureUiState(
                touchpointToActivate = state.touchpointToActivate,
                error = error
              )
          }
        ).asModalScreen()

      is SendingActivationToServerFailureUiState ->
        SendingActivationToServerFailureModel(
          props = props,
          state = state
        ).asModalScreen()

      is SendingActivationToServerSuccessUiState ->
        SendingActivationToServerSuccessModel(
          props = props,
          requiredHwProofOfPossession = state.requiredHwProofOfPossession
        ).asModalScreen()
    }
  }

  @Composable
  private fun VerifyingProofOfHwPossessionModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: VerifyingProofOfHwPossessionUiState,
    goToActivationInstructions: () -> Unit,
    onSuccess: (HwFactorProofOfPossession) -> Unit,
  ): ScreenModel {
    return proofOfPossessionNfcStateMachine.model(
      props =
        ProofOfPossessionNfcProps(
          request = Request.HwKeyProof(onSuccess),
          fullAccountId = props.fullAccountId,
          fullAccountConfig = props.fullAccountConfig,
          onBack = goToActivationInstructions,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onTokenRefresh = {
            // Provide a screen model to show while the token is being refreshed.
            // We want this to be the same as [ActivationApprovalInstructionsUiState]
            // but with the button in a loading state
            NotificationOperationApprovalInstructionsFormScreenModel(
              onExit = props.onClose,
              operationDescriptiton = operationDescription(state.touchpointToActivate.formattedDisplayValue),
              isApproveButtonLoading = true,
              errorBottomSheetState = ErrorBottomSheetState.Hidden,
              onApprove = {
                // No-op. Button is loading.
              }
            )
          },
          onTokenRefreshError = { isConnectivityError, _ ->
            // Provide a screen model to show if the token refresh results in an error.
            // We want this to be the same as [ActivationApprovalInstructionsUiState]
            // but with the error bottom sheet showing
            NotificationOperationApprovalInstructionsFormScreenModel(
              onExit = props.onClose,
              operationDescriptiton = operationDescription(state.touchpointToActivate.formattedDisplayValue),
              isApproveButtonLoading = false,
              errorBottomSheetState =
                ErrorBottomSheetState.Showing(
                  isConnectivityError = isConnectivityError,
                  onClosed = goToActivationInstructions
                ),
              onApprove = {
                // No-op. Showing error sheet
              }
            )
          }
        )
    )
  }

  @Composable
  private fun EnteringTouchpointModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: EnteringTouchpointUiState,
    onSubmissionToServerSuccess: (touchpoint: NotificationTouchpoint) -> Unit,
  ): ScreenModel {
    var mutableSubmissionState: NotificationTouchpointSubmissionState by remember {
      mutableStateOf(None)
    }

    // Side Effect: submit number to server
    when (val submission = mutableSubmissionState) {
      is None -> Unit
      is SendingTouchpointToServer -> {
        SendTouchpointToServerEffect(
          touchpoint = submission.touchpoint,
          onResult = { result ->
            result
              .onSuccess { touchpointWithId ->
                onSubmissionToServerSuccess(touchpointWithId)
              }
              .onFailure { submission.onError(it) }
            mutableSubmissionState = None
          },
          props = props,
          state = state
        )
      }
    }

    // Helper function
    fun onSubmitTouchpoint(
      touchpoint: NotificationTouchpoint,
      onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
    ) {
      mutableSubmissionState =
        SendingTouchpointToServer(
          touchpoint = touchpoint,
          onError = onError
        )
    }

    // Including this call directly in the onError lambda for the phoneNumberInputUiStateMachine
    // causes a recomposition for unknown reasons. Putting the specific call in a `remember`
    // lambda prevents the recomposition. Because it's an injected property, this should cause
    // no behavior differences.
    val phoneNotAvailable: (() -> Unit) =
      remember(uiErrorHintSubmitter) {
        {
          uiErrorHintSubmitter.phoneNotAvailable()
        }
      }

    // Return model
    return when (props.touchpointType) {
      PhoneNumber -> {
        phoneNumberInputUiStateMachine.model(
          props =
            PhoneNumberInputUiProps(
              dataInputStyle = props.entryPoint.dataInputStyle(),
              prefillValue = (state.touchpointPrefill as? PhoneNumberTouchpoint)?.value,
              subline = if (props.entryPoint is Recovery) {
                "We’ll only use this phone number to notify you of wallet recovery attempts and privacy updates, nothing else."
              } else {
                null
              },
              primaryButtonText =
                when (props.entryPoint) {
                  is Onboarding -> "Skip for Now"
                  is Recovery -> "Use a different number"
                  is Settings -> "Got it"
                },
              primaryButtonOnClick =
                when (props.entryPoint) {
                  is Onboarding -> props.entryPoint.onSkip
                  is Recovery -> null
                  is Settings -> null
                },
              secondaryButtonText =
                when (props.entryPoint) {
                  is Onboarding -> "Use Different Country Number"
                  is Recovery -> "Skip"
                  is Settings -> null
                },
              secondaryButtonOnClick =
                when (props.entryPoint) {
                  is Onboarding -> null
                  is Recovery -> props.entryPoint.onSkip
                  is Settings -> null
                },
              onClose = props.onClose,
              onSubmitPhoneNumber = { phoneNumber, onError ->
                onSubmitTouchpoint(
                  touchpoint =
                    PhoneNumberTouchpoint(
                      touchpointId = "",
                      value = phoneNumber
                    ),
                  onError = { error ->
                    if (f8toErrorCode(error) == AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE) {
                      phoneNotAvailable()
                    }
                    onError(error)
                  }
                )
              },
              skipBottomSheetProvider =
                when (props.entryPoint) {
                  is Onboarding -> props.entryPoint.skipBottomSheetProvider
                  else -> null
                }
            )
        )
      }

      Email -> {
        emailInputUiStateMachine.model(
          props =
            EmailInputUiProps(
              dataInputStyle = props.entryPoint.dataInputStyle(),
              previousEmail = (state.touchpointPrefill as? EmailTouchpoint)?.value,
              subline = if (props.entryPoint is Recovery) {
                "We’ll only use this email to notify you of wallet recovery attempts and privacy updates, nothing else."
              } else {
                null
              },
              onClose = props.onClose,
              skipBottomSheetProvider =
                when (props.entryPoint) {
                  is Onboarding -> props.entryPoint.skipBottomSheetProvider
                  else -> null
                },
              onEmailEntered = { email, onError ->
                onSubmitTouchpoint(
                  touchpoint =
                    EmailTouchpoint(
                      touchpointId = "",
                      value = email
                    ),
                  onError = onError
                )
              }
            )
        )
      }
    }
  }

  @Composable
  private fun EnteringVerificationCodeModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: EnteringVerificationCodeUiState,
    onBack: () -> Unit,
    onCodeEntered: (verificationCode: String) -> Unit,
  ): ScreenModel {
    var resendCodeCallbacks: ResendCodeCallbacks? by remember { mutableStateOf(null) }

    // Side Effect: Resend if callbacks are present

    // If the [resendCodeCallbacks] have been set on the state, we
    // need to resend the touchpoint to the server in order for
    // the verification code to be resent to the customer.
    resendCodeCallbacks?.let { callbacks ->
      SendTouchpointToServerEffect(
        touchpoint = state.touchpointToVerify,
        onResult = { result ->
          result
            .onSuccess { callbacks.onSuccess() }
            .onFailure { callbacks.onError(it is F8eError.ConnectivityError) }
          resendCodeCallbacks = null
        },
        props = props,
        state = state
      )
    }

    // Return model
    return verificationCodeInputStateMachine.model(
      props =
        VerificationCodeInputProps(
          title =
            when (state.touchpointToVerify) {
              is PhoneNumberTouchpoint -> "Verify your phone number"
              is EmailTouchpoint -> "Verify your email"
            },
          subtitle = "We sent a code to ${state.touchpointToVerify.formattedDisplayValue}.",
          expectedCodeLength = 6,
          notificationTouchpoint = state.touchpointToVerify,
          onBack = onBack,
          onCodeEntered = onCodeEntered,
          onResendCode = {
            resendCodeCallbacks = it
          },
          skipBottomSheetProvider =
            when (props.entryPoint) {
              is Onboarding -> props.entryPoint.skipBottomSheetProvider
              else -> null
            },
          screenId =
            when (props.touchpointType) {
              Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_CODE
              PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_ENTERING_CODE
            }
        )
    )
  }

  @Composable
  private fun SendingVerificationCodeToServerModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: SendingVerificationCodeToServerUiState,
    setState: (NotificationTouchpointInputAndVerificationUiState) -> Unit,
  ): BodyModel {
    // Side effect: send code to server
    LaunchedEffect("send-verification-code", state) {
      notificationTouchpointService
        .verifyTouchpoint(
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          fullAccountId = props.fullAccountId,
          touchpointId = state.touchpointToVerify.touchpointId,
          verificationCode = state.verificationCode
        )
        .onSuccess {
          // Determine the next step based on the entry point.
          // In onboarding, HW proof of possession is not required, so we can send the activation
          // request to the server directly. Otherwise, we need the customer to perform a HW tap
          // to get the proof of possession, so go to the activation instructions for that.
          when (props.entryPoint) {
            is Onboarding, is Recovery ->
              setState(
                SendingActivationToServerUiState(
                  touchpointToActivate = state.touchpointToVerify,
                  hwFactorProofOfPossession = null
                )
              )
            is Settings ->
              setState(
                ActivationApprovalInstructionsUiState(
                  touchpointToActivate = state.touchpointToVerify,
                  errorBottomSheetState = ErrorBottomSheetState.Hidden
                )
              )
          }
        }
        .onFailure { error ->
          setState(
            SendingVerificationCodeToServerFailureUiState(
              touchpointToVerify = state.touchpointToVerify,
              error = error
            )
          )
        }
    }

    // Return model
    return LoadingSuccessBodyModel(
      id =
        when (props.touchpointType) {
          Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_CODE_TO_SERVER
          PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_CODE_TO_SERVER
        },
      state = LoadingSuccessBodyModel.State.Loading
    )
  }

  @Composable
  private fun SendingVerificationCodeToServerFailureModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: SendingVerificationCodeToServerFailureUiState,
    onBackToTouchpointInput: () -> Unit,
    onBackToCodeInput: () -> Unit,
  ): BodyModel {
    return when (state.error) {
      is F8eError.SpecificClientError ->
        when (state.error.errorCode) {
          VerifyTouchpointClientErrorCode.CODE_EXPIRED ->
            ErrorFormBodyModel(
              title = state.touchpointToVerify.verificationErrorTitle(),
              subline = "Your verification code has expired. Please submit your contact details again.",
              primaryButton =
                ButtonDataModel(
                  text = "Back",
                  onClick = onBackToTouchpointInput
                ),
              eventTrackerScreenId =
                when (props.touchpointType) {
                  Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_CODE_EXPIRED_ERROR
                  PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_CODE_EXPIRED_ERROR
                }
            )

          VerifyTouchpointClientErrorCode.CODE_MISMATCH ->
            ErrorFormBodyModel(
              title = state.touchpointToVerify.verificationErrorTitle(),
              subline = "The verification code was incorrect. Please try again.",
              primaryButton = ButtonDataModel(text = "Back", onClick = onBackToCodeInput),
              eventTrackerScreenId =
                when (props.touchpointType) {
                  Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_INCORRECT_CODE_ERROR
                  PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_INCORRECT_CODE_ERROR
                }
            )
        }

      else -> {
        val isConnectivityError = state.error is F8eError.ConnectivityError
        NetworkErrorFormBodyModel(
          title = state.touchpointToVerify.verificationErrorTitle(),
          isConnectivityError = isConnectivityError,
          onBack = if (isConnectivityError) onBackToCodeInput else onBackToTouchpointInput,
          eventTrackerScreenId =
            when (props.touchpointType) {
              Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_CODE_TO_SERVER_ERROR
              PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_CODE_TO_SERVER_ERROR
            }
        )
      }
    }
  }

  @Composable
  private fun SendingActivationToServerModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: SendingActivationToServerUiState,
    onSuccess: () -> Unit,
    onFailure: (NetworkingError) -> Unit,
  ): BodyModel {
    // Side effect: send activation request to server
    LaunchedEffect("send-activation", state) {
      notificationTouchpointService
        .activateTouchpoint(
          f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
          fullAccountId = props.fullAccountId,
          touchpointId = state.touchpointToActivate.touchpointId,
          hwFactorProofOfPossession = state.hwFactorProofOfPossession
        )
        .onSuccess {
          notificationTouchpointDao.storeTouchpoint(state.touchpointToActivate)
          onSuccess()
        }
        .onFailure(onFailure)
    }

    // Return model
    return LoadingSuccessBodyModel(
      id =
        when (props.touchpointType) {
          Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER
          PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_ACTIVATION_TO_SERVER
        },
      state = LoadingSuccessBodyModel.State.Loading
    )
  }

  @Composable
  private fun SendingActivationToServerFailureModel(
    props: NotificationTouchpointInputAndVerificationProps,
    state: SendingActivationToServerFailureUiState,
  ): BodyModel {
    val isConnectivityError = state.error is NetworkError
    return NetworkErrorFormBodyModel(
      title = state.touchpointToActivate.activationErrorTitle(),
      isConnectivityError = isConnectivityError,
      onBack = props.onClose,
      eventTrackerScreenId =
        when (props.touchpointType) {
          Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR
          PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR
        }
    )
  }

  @Composable
  private fun SendingActivationToServerSuccessModel(
    props: NotificationTouchpointInputAndVerificationProps,
    requiredHwProofOfPossession: Boolean,
  ): BodyModel {
    // Side effect
    LaunchedEffect("end-flow-after-delay") {
      delayer.delay(3.seconds)
      props.onSuccess()
    }

    // Return model
    return LoadingSuccessBodyModel(
      id =
        when (props.touchpointType) {
          Email -> NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL
          PhoneNumber -> NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_SMS
        },
      state = LoadingSuccessBodyModel.State.Success,
      message =
        // If the activation didn't require HW proof of possession, just show 'Verified' because
        // that was the main action the customer was taking
        when (requiredHwProofOfPossession) {
          true -> "Approved"
          false -> "Verified"
        }
    )
  }

  @Composable
  private fun SendTouchpointToServerEffect(
    touchpoint: NotificationTouchpoint,
    onResult: (Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>>) -> Unit,
    props: NotificationTouchpointInputAndVerificationProps,
    state: NotificationTouchpointInputAndVerificationUiState,
  ) {
    LaunchedEffect("send-touchpoint", state) {
      val result =
        notificationTouchpointService
          .addTouchpoint(
            f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
            fullAccountId = props.fullAccountId,
            touchpoint = touchpoint
          )
      onResult(result)
    }
  }
}

sealed interface NotificationTouchpointInputAndVerificationUiState {
  /**
   * The customer is entering the touchpoint information (either sms number or email).
   * @property touchpointPrefill: The touchpoint to show pre-filled, if any.
   */
  data class EnteringTouchpointUiState(
    val touchpointPrefill: NotificationTouchpoint?,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * The customer is entering the verification code sent to the previously entered touchpoint.
   * @property touchpointToVerify: The touchpoint being verified.
   */
  data class EnteringVerificationCodeUiState(
    val touchpointToVerify: NotificationTouchpoint,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * We are sending the verification code to the server to validate it.
   * @property verificationCode: The code to send to the server
   */
  data class SendingVerificationCodeToServerUiState(
    val touchpointToVerify: NotificationTouchpoint,
    val verificationCode: String,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * Failure state for error when sending verification code to the server
   */
  data class SendingVerificationCodeToServerFailureUiState(
    val touchpointToVerify: NotificationTouchpoint,
    val error: F8eError<VerifyTouchpointClientErrorCode>,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * We are showing instructions for the customer to activate the new touchpoint via NFC tap.
   * Only shown if proof of HW possession will be required for the activation call.
   */
  data class ActivationApprovalInstructionsUiState(
    val touchpointToActivate: NotificationTouchpoint,
    val errorBottomSheetState: ErrorBottomSheetState,
  ) : NotificationTouchpointInputAndVerificationUiState {
    sealed interface ErrorBottomSheetState {
      data object Hidden : ErrorBottomSheetState

      data class Showing(
        val isConnectivityError: Boolean,
        val onClosed: () -> Unit,
      ) : ErrorBottomSheetState
    }
  }

  /**
   * The customer is verifying proof of HW possession via NFC tap in order to activate this
   * touchpoint on their account. This step will only be reached if specified by the props.
   */
  data class VerifyingProofOfHwPossessionUiState(
    val touchpointToActivate: NotificationTouchpoint,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * We are sending the request to active the touchpoint to the server to finalize adding it to
   * the customer's profile.
   * @property hwFactorProofOfPossession: Proof of HW possession if verifying after the user has
   * onboarded
   */
  data class SendingActivationToServerUiState(
    val touchpointToActivate: NotificationTouchpoint,
    val hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * Success state when sending activation request to the server
   * @property requiredHwProofOfPossession: Whether the call needed HW proof of possession to
   * determine what success message to show to the customer
   */
  data class SendingActivationToServerSuccessUiState(
    val requiredHwProofOfPossession: Boolean,
  ) : NotificationTouchpointInputAndVerificationUiState

  /**
   * Failure state for error when sending activation request to the server
   */
  data class SendingActivationToServerFailureUiState(
    val touchpointToActivate: NotificationTouchpoint,
    val error: NetworkingError,
  ) : NotificationTouchpointInputAndVerificationUiState
}

private fun operationDescription(formattedDisplayValue: String) =
  "Notifications will be sent to $formattedDisplayValue"

private sealed interface NotificationTouchpointSubmissionState {
  data object None : NotificationTouchpointSubmissionState

  /**
   * We are sending the touchpoint to the server to initiate adding it to
   * the customer's profile.
   * @property touchpoint: The touchpoint to send to the server
   */
  data class SendingTouchpointToServer(
    val touchpoint: NotificationTouchpoint,
    val onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) : NotificationTouchpointSubmissionState
}

private fun NotificationTouchpoint.verificationErrorTitle() =
  when (this) {
    is PhoneNumberTouchpoint -> "We couldn’t verify this phone number"
    is EmailTouchpoint -> "We couldn’t verify this email address"
  }

private fun NotificationTouchpoint.activationErrorTitle() =
  when (this) {
    is PhoneNumberTouchpoint -> "We couldn’t activate this phone number"
    is EmailTouchpoint -> "We couldn’t activate this email address"
  }

private fun EntryPoint.dataInputStyle() =
  when (this) {
    is Onboarding, is Recovery -> Enter
    is Settings -> Edit
  }

/**
 * Helper function for getting the error code when country not allowed, or null. Moved here to clean
 * up the code above.
 */
private fun f8toErrorCode(
  er: F8eError<AddTouchpointClientErrorCode>,
): AddTouchpointClientErrorCode? = (er as? F8eError.SpecificClientError)?.errorCode

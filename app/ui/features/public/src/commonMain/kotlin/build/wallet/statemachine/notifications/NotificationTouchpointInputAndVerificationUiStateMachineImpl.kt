package build.wallet.statemachine.notifications

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.account.HardwareType
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.input.*
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.*
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState
import build.wallet.statemachine.notifications.NotificationTouchpointSubmissionRequest.SendingTouchpointToServer
import build.wallet.statemachine.root.ActionSuccessDuration
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay

@BitkeyInject(ActivityScope::class)
class NotificationTouchpointInputAndVerificationUiStateMachineImpl(
  private val emailInputUiStateMachine: EmailInputUiStateMachine,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
  private val phoneNumberInputUiStateMachine: PhoneNumberInputUiStateMachine,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val verificationCodeInputStateMachine: VerificationCodeInputStateMachine,
  private val uiErrorHintSubmitter: UiErrorHintSubmitter,
  private val actionSuccessDuration: ActionSuccessDuration,
  private val accountConfigService: AccountConfigService,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
) : NotificationTouchpointInputAndVerificationUiStateMachine {
  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun model(props: NotificationTouchpointInputAndVerificationProps): ScreenModel {
    val f8eEnvironment =
      remember { accountConfigService.activeOrDefaultConfig().value.f8eEnvironment }
    var uiState: NotificationTouchpointInputAndVerificationUiState by remember {
      mutableStateOf(EnteringTouchpointUiState(touchpointPrefill = null))
    }
    val usSmsEnabled by remember { usSmsFeatureFlag.flagValue() }.collectAsState()

    // Reactively load the stored touchpoint as prefill so returning users see their value
    // pre-populated. Collected as state so the value is available on the first recomposition
    // after the DAO emits.
    val storedTouchpoint: NotificationTouchpoint? by when (props.touchpointType) {
      Email -> remember { notificationTouchpointDao.emailTouchpoint() }
      PhoneNumber -> remember { notificationTouchpointDao.phoneTouchpoint() }
    }.collectAsState(initial = null)

    return when (val state = uiState) {
      is EnteringTouchpointUiState -> {
        var mutableSubmissionState: NotificationTouchpointSubmissionRequest? by remember {
          mutableStateOf(null)
        }

        // Side Effect: submit number to server
        when (val submission = mutableSubmissionState) {
          is SendingTouchpointToServer -> {
            LaunchedEffect("send-touchpoint") {
              notificationTouchpointF8eClient
                .addTouchpoint(
                  f8eEnvironment = f8eEnvironment,
                  accountId = props.accountId,
                  touchpoint = submission.touchpoint
                )
                .onSuccess { touchpointWithId ->
                  if (props.touchpointType == PhoneNumber) {
                    uiErrorHintSubmitter.phoneNone()
                  }
                  uiState = EnteringVerificationCodeUiState(touchpointToVerify = touchpointWithId)
                }
                .onFailure(submission.onError)

              mutableSubmissionState = null
            }
          }
          else -> Unit
        }

        // Including this call directly in the onError lambda for the phoneNumberInputUiStateMachine
        // causes a recomposition for unknown reasons. Putting the specific call in a `remember`
        // lambda prevents the recomposition. Because it's an injected property, this should cause
        // no behavior differences.
        val phoneNotAvailable: (() -> Unit) = remember(uiErrorHintSubmitter) {
          uiErrorHintSubmitter::phoneNotAvailable
        }

        // Return model
        when (props.touchpointType) {
          PhoneNumber ->
            phoneNumberInputUiStateMachine.model(
              props = PhoneNumberInputUiProps(
                dataInputStyle = props.entryPoint.dataInputStyle(storedTouchpoint is PhoneNumberTouchpoint),
                prefillValue = (state.touchpointPrefill as? PhoneNumberTouchpoint)?.value
                  ?: (storedTouchpoint as? PhoneNumberTouchpoint)?.value,
                subline = if (props.onLearnMore == null && props.entryPoint is OnboardingAndRecovery) {
                  "We'll only use this phone number to notify you of wallet recovery attempts and privacy updates, nothing else."
                } else {
                  null
                },
                sublineModel = props.onLearnMore?.let { criticalAlertsSublineWithLearnMore(it) },
                isCloseButton = props.entryPoint is Settings,
                primaryButtonText =
                  when (props.entryPoint) {
                    is OnboardingAndRecovery -> "Use a different number"
                    is Settings -> "Got it"
                  },
                primaryButtonOnClick = null,
                secondaryButtonText =
                  when (props.entryPoint) {
                    is OnboardingAndRecovery -> "Skip"
                    is Settings -> null
                  },
                secondaryButtonOnClick =
                  when (props.entryPoint) {
                    is OnboardingAndRecovery -> props.entryPoint.onSkip
                    is Settings -> null
                  },
                onClose = props.onClose,
                onSubmitPhoneNumber = { phoneNumber, onError ->
                  val prefillPhone = (storedTouchpoint as? PhoneNumberTouchpoint)?.value
                  if (phoneNumber == prefillPhone) {
                    // Unchanged — skip server round-trip and re-verification.
                    // Clear any stale phone error hint (e.g. NotAvailableInYourCountry) so
                    // downstream settings screens don't incorrectly show SMS as unavailable.
                    uiErrorHintSubmitter.phoneNone()
                    props.onSuccess()
                  } else {
                    mutableSubmissionState =
                      SendingTouchpointToServer(
                        touchpoint = PhoneNumberTouchpoint(touchpointId = "", value = phoneNumber),
                        onError = { error ->
                          if (error.isUnsupportedCountryCode() &&
                            !usSmsEnabled.value &&
                            telephonyCountryCodeProvider.isCountry("us")
                          ) {
                            phoneNotAvailable()
                          }
                          onError(error)
                        }
                      )
                  }
                },
                onSkipSecondaryButton = (props.entryPoint as? OnboardingAndRecovery)?.onSkip
              )
            )

          Email -> {
            emailInputUiStateMachine.model(
              props = EmailInputUiProps(
                dataInputStyle = props.entryPoint.dataInputStyle(storedTouchpoint is EmailTouchpoint),
                previousEmail = (state.touchpointPrefill as? EmailTouchpoint)?.value
                  ?: (storedTouchpoint as? EmailTouchpoint)?.value,
                subline = if (props.onLearnMore == null && props.entryPoint is OnboardingAndRecovery) {
                  "We'll only use this email to notify you of wallet recovery attempts and privacy updates, nothing else."
                } else {
                  null
                },
                sublineModel = props.onLearnMore?.let { criticalAlertsSublineWithLearnMore(it) },
                onClose = props.onClose,
                isCloseButton = props.entryPoint is Settings,
                onEmailEntered = { email, onError ->
                  val prefillEmail = (storedTouchpoint as? EmailTouchpoint)?.value
                  if (email == prefillEmail) {
                    // Unchanged — skip server round-trip and re-verification
                    props.onSuccess()
                  } else {
                    mutableSubmissionState = SendingTouchpointToServer(
                      touchpoint = EmailTouchpoint(touchpointId = "", value = email),
                      onError = onError
                    )
                  }
                }
              )
            )
          }
        }
      }

      is EnteringVerificationCodeUiState -> {
        var resendCodeCallbacks: ResendCodeCallbacks? by remember { mutableStateOf(null) }
        // Side Effect: Resend if callbacks are present

        // If the [resendCodeCallbacks] have been set on the state, we
        // need to resend the touchpoint to the server in order for
        // the verification code to be resent to the customer.
        resendCodeCallbacks?.let { callbacks ->
          LaunchedEffect("send-touchpoint") {
            notificationTouchpointF8eClient
              .addTouchpoint(
                f8eEnvironment = f8eEnvironment,
                accountId = props.accountId,
                touchpoint = state.touchpointToVerify
              )
              .onSuccess { callbacks.onSuccess() }
              .onFailure { callbacks.onError(it is F8eError.ConnectivityError) }
            resendCodeCallbacks = null
          }
        }
        // Return model
        verificationCodeInputStateMachine.model(
          props = VerificationCodeInputProps(
            title = when (state.touchpointToVerify) {
              is PhoneNumberTouchpoint -> "Verify your phone number"
              is EmailTouchpoint -> "Verify your email"
            },
            subtitle = "We sent a code to ${state.touchpointToVerify.formattedDisplayValue}.",
            expectedCodeLength = 6,
            notificationTouchpoint = state.touchpointToVerify,
            onBack = {
              uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToVerify)
            },
            onCodeEntered = { verificationCode: String ->
              uiState = SendingVerificationCodeToServerUiState(
                touchpointToVerify = state.touchpointToVerify,
                verificationCode = verificationCode
              )
            },
            onResendCode = { resendCodeCallbacks = it },
            screenId = when (props.touchpointType) {
              Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_CODE
              PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_ENTERING_CODE
            }
          )
        )
      }

      is SendingVerificationCodeToServerUiState -> {
        // Side effect: send code to server
        LaunchedEffect("send-verification-code", key2 = state) {
          notificationTouchpointF8eClient
            .verifyTouchpoint(
              f8eEnvironment = f8eEnvironment,
              accountId = props.accountId,
              touchpointId = state.touchpointToVerify.touchpointId,
              verificationCode = state.verificationCode
            )
            .onSuccess {
              val requiresHwVerification = when (val entryPoint = props.entryPoint) {
                is Settings -> true
                is OnboardingAndRecovery -> entryPoint.fullAccount?.config?.hardwareType == HardwareType.W3
              }
              uiState = if (requiresHwVerification) {
                ActivationApprovalInstructionsUiState(
                  touchpointToActivate = state.touchpointToVerify,
                  errorBottomSheetState = ErrorBottomSheetState.Hidden
                )
              } else {
                SendingActivationToServerUiState(
                  touchpointToActivate = state.touchpointToVerify,
                  proof = null
                )
              }
            }
            .onFailure { error ->
              SendingVerificationCodeToServerFailureUiState(
                touchpointToVerify = state.touchpointToVerify,
                error = error
              )
              uiState = SendingVerificationCodeToServerFailureUiState(
                touchpointToVerify = state.touchpointToVerify,
                error = error
              )
            }
        }
        // Return model
        LoadingBodyModel(
          id = when (props.touchpointType) {
            Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_CODE_TO_SERVER
            PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_CODE_TO_SERVER
          }
        ).asModalScreen()
      }

      is SendingVerificationCodeToServerFailureUiState -> {
        val onBackToTouchpointInput = {
          uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToVerify)
        }
        val onBackToCodeInput = {
          uiState = EnteringVerificationCodeUiState(state.touchpointToVerify)
        }
        when (state.error) {
          is F8eError.SpecificClientError ->
            when (state.error.errorCode) {
              VerifyTouchpointClientErrorCode.CODE_EXPIRED ->
                ErrorFormBodyModel(
                  title = state.touchpointToVerify.verificationErrorTitle(),
                  subline = "Your verification code has expired. Please submit your contact details again.",
                  primaryButton = ButtonDataModel(
                    text = "Back",
                    onClick = onBackToTouchpointInput
                  ),
                  eventTrackerScreenId = when (props.touchpointType) {
                    Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_CODE_EXPIRED_ERROR
                    PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_CODE_EXPIRED_ERROR
                  }
                )

              VerifyTouchpointClientErrorCode.CODE_MISMATCH ->
                ErrorFormBodyModel(
                  title = state.touchpointToVerify.verificationErrorTitle(),
                  subline = "The verification code was incorrect. Please try again.",
                  primaryButton = ButtonDataModel(text = "Back", onClick = onBackToCodeInput),
                  eventTrackerScreenId = when (props.touchpointType) {
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
              eventTrackerScreenId = when (props.touchpointType) {
                Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_CODE_TO_SERVER_ERROR
                PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_CODE_TO_SERVER_ERROR
              }
            )
          }
        }.asModalScreen()
      }

      is ActivationApprovalInstructionsUiState ->
        NotificationOperationApprovalInstructionsFormScreenModel(
          onExit = { uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToActivate) },
          headline = props.entryPoint.activationApprovalHeadline(),
          operationDescription = props.entryPoint.activationApprovalDescription(state.touchpointToActivate),
          primaryButtonText = props.entryPoint.activationApprovalPrimaryButtonText(),
          isApproveButtonLoading = false,
          errorBottomSheetState = ErrorBottomSheetState.Hidden,
          onApprove = {
            uiState = VerifyingProofOfHwPossessionUiState(
              touchpointToActivate = state.touchpointToActivate
            )
          }
        )

      is VerifyingProofOfHwPossessionUiState -> {
        val fullAccount = when (val ep = props.entryPoint) {
          is Settings -> ep.fullAccount
          is OnboardingAndRecovery -> requireNotNull(ep.fullAccount) {
            "fullAccount is required for HW verification in OnboardingAndRecovery entry point"
          }
        }

        val goToActivationInstructions = {
          uiState = ActivationApprovalInstructionsUiState(
            touchpointToActivate = state.touchpointToActivate,
            errorBottomSheetState = ErrorBottomSheetState.Hidden
          )
        }

        val actionProofType = state.touchpointToActivate.toActionProofType()

        hardwareAuthUiStateMachine.model(
          props =
            HardwareAuthUiProps(
              account = fullAccount,
              actionProofType = actionProofType,
              segment = NotificationsAppSegment,
              actionDescription = "Activating notification touchpoint",
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              onSuccess = { proof ->
                uiState = SendingActivationToServerUiState(
                  touchpointToActivate = state.touchpointToActivate,
                  proof = proof
                )
              },
              shouldLock = props.entryPoint is Settings,
              onBack = goToActivationInstructions,
              onTokenRefresh = {
                // Provide a screen model to show while the token is being refreshed.
                // We want this to be the same as [ActivationApprovalInstructionsUiState]
                // but with the button in a loading state
                NotificationOperationApprovalInstructionsFormScreenModel(
                  onExit = { uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToActivate) },
                  headline = props.entryPoint.activationApprovalHeadline(),
                  operationDescription = props.entryPoint.activationApprovalDescription(state.touchpointToActivate),
                  primaryButtonText = props.entryPoint.activationApprovalPrimaryButtonText(),
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
                  onExit = { uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToActivate) },
                  headline = props.entryPoint.activationApprovalHeadline(),
                  operationDescription = props.entryPoint.activationApprovalDescription(state.touchpointToActivate),
                  primaryButtonText = props.entryPoint.activationApprovalPrimaryButtonText(),
                  isApproveButtonLoading = false,
                  errorBottomSheetState = ErrorBottomSheetState.Showing(
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

      is SendingActivationToServerUiState -> {
        // Side effect: send activation request to server
        LaunchedEffect("send-activation", key2 = state) {
          notificationTouchpointF8eClient
            .activateTouchpoint(
              f8eEnvironment = f8eEnvironment,
              accountId = props.accountId,
              touchpointId = state.touchpointToActivate.touchpointId,
              proof = state.proof
            )
            .onSuccess {
              notificationTouchpointDao.storeTouchpoint(state.touchpointToActivate)
              uiState = SendingActivationToServerSuccessUiState(
                requiredHwProofOfPossession = state.proof != null
              )
            }
            .onFailure(
              action = { error: NetworkingError ->
                uiState = SendingActivationToServerFailureUiState(
                  touchpointToActivate = state.touchpointToActivate,
                  error = error
                )
              }
            )
        }
        // Return model
        LoadingBodyModel(
          id = when (props.touchpointType) {
            Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER
            PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_ACTIVATION_TO_SERVER
          }
        ).asModalScreen()
      }

      is SendingActivationToServerFailureUiState -> {
        NetworkErrorFormBodyModel(
          title = state.touchpointToActivate.activationErrorTitle(),
          isConnectivityError = state.error is NetworkError,
          onBack = { uiState = EnteringTouchpointUiState(touchpointPrefill = state.touchpointToActivate) },
          eventTrackerScreenId = when (props.touchpointType) {
            Email -> NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR
            PhoneNumber -> NotificationsEventTrackerScreenId.SMS_INPUT_SENDING_ACTIVATION_TO_SERVER_ERROR
          }
        ).asModalScreen()
      }

      is SendingActivationToServerSuccessUiState -> {
        // Side effect
        LaunchedEffect("end-flow-after-delay") {
          delay(actionSuccessDuration.value)
          props.onSuccess()
        }
        // Return model
        LoadingSuccessBodyModel(
          id = when (props.touchpointType) {
            Email -> NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL
            PhoneNumber -> NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_SMS
          },
          state = LoadingSuccessBodyModel.State.Success,
          // If the activation didn't require HW proof of possession, just show 'Verified' because
          // that was the main action the customer was taking
          message = if (state.requiredHwProofOfPossession) "Approved" else "Verified"
        ).asModalScreen()
      }
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
   * Activating the touchpoint on the server to finalize adding it to the customer's profile.
   *
   * @property proof authorization proof, if required (e.g. HW proof-of-possession after onboarding).
   */
  data class SendingActivationToServerUiState(
    val touchpointToActivate: NotificationTouchpoint,
    val proof: PrivilegedActionProof?,
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

private fun EntryPoint.activationApprovalHeadline() =
  when (this) {
    is OnboardingAndRecovery -> "Confirm details on your Bitkey"
    is Settings -> "Approve this change with your Bitkey device"
  }

private fun EntryPoint.activationApprovalPrimaryButtonText() =
  when (this) {
    is OnboardingAndRecovery -> "Continue"
    is Settings -> "Approve"
  }

private fun EntryPoint.activationApprovalDescription(touchpoint: NotificationTouchpoint) =
  when (this) {
    is OnboardingAndRecovery ->
      when (touchpoint) {
        is EmailTouchpoint ->
          "Your Bitkey must approve changes to your security settings. Review and approve saving ${touchpoint.formattedDisplayValue} as your recovery email."
        is PhoneNumberTouchpoint ->
          "Your Bitkey must approve changes to your security settings. Review and approve saving ${touchpoint.formattedDisplayValue} as your recovery phone number."
      }
    is Settings -> "Notifications will be sent to ${touchpoint.formattedDisplayValue}"
  }

private sealed interface NotificationTouchpointSubmissionRequest {
  /**
   * We are sending the touchpoint to the server to initiate adding it to
   * the customer's profile.
   * @property touchpoint: The touchpoint to send to the server
   */
  data class SendingTouchpointToServer(
    val touchpoint: NotificationTouchpoint,
    val onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) : NotificationTouchpointSubmissionRequest
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

private fun EntryPoint.dataInputStyle(hasExistingTouchpoint: Boolean) =
  when (this) {
    is OnboardingAndRecovery -> Enter
    is Settings -> if (hasExistingTouchpoint) Edit else Enter
  }

private fun F8eError<AddTouchpointClientErrorCode>.isUnsupportedCountryCode(): Boolean =
  this is F8eError.SpecificClientError && errorCode == AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE

/**
 * Maps a [NotificationTouchpoint] to the corresponding [ActionProofType] for hardware signing.
 * Always uses Set regardless of whether the touchpoint is new or replacing an existing one.
 */
private fun NotificationTouchpoint.toActionProofType(): ActionProofType =
  when (this) {
    is EmailTouchpoint ->
      ActionProofType.SetRecoveryEmail(email = value.value, touchpointId = touchpointId)
    is PhoneNumberTouchpoint ->
      ActionProofType.SetRecoveryPhone(phone = value.formattedE164Value, touchpointId = touchpointId)
  }

/**
 * App segment for notifications-related error tracking.
 */
internal object NotificationsAppSegment : AppSegment {
  override val id: String = "Notifications"
}

private const val CRITICAL_ALERTS_SUBLINE =
  "You will only receive alerts about recovery attempts, inheritance, and privacy updates.\nLearn more"

private fun criticalAlertsSublineWithLearnMore(onLearnMore: () -> Unit): LabelModel.LinkSubstringModel =
  LabelModel.LinkSubstringModel.from(
    string = CRITICAL_ALERTS_SUBLINE,
    substringToOnClick = mapOf("Learn more" to onLearnMore),
    underline = false,
    bold = false,
    color = LabelModel.Color.PRIMARY
  )


package build.wallet.statemachine.notifications

import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock.*
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.platform.settings.TelephonyCountryCodeProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.*
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.matchers.shouldBeLoading
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

// Large end-to-end coverage for touchpoint input and verification; splitting would hurt cohesion.
@Suppress("LargeClass")
class NotificationTouchpointInputAndVerificationUiStateMachineImplTests : FunSpec({

  val onCloseCalls = turbines.create<Unit>("on close calls")
  val onSkipCalls = turbines.create<Unit>("on skip calls")

  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationTouchpointF8eClient = NotificationTouchpointF8eClientMock(turbines::create)
  val accountConfigService = AccountConfigServiceFake()

  val featureFlagDao = FeatureFlagDaoFake()
  val usSmsFeatureFlag = UsSmsFeatureFlag(featureFlagDao)
  val phoneNotAvailableCalls = turbines.create<Unit>("phone not available calls")
  val phoneNoneCalls = turbines.create<Unit>("phone none calls")
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()

  val uiErrorHintSubmitter = object : UiErrorHintSubmitter {
    override fun phoneNone() {
      phoneNoneCalls.add(Unit)
    }

    override fun phoneNotAvailable() {
      phoneNotAvailableCalls.add(Unit)
    }
  }

  fun createStateMachine() =
    NotificationTouchpointInputAndVerificationUiStateMachineImpl(
      emailInputUiStateMachine =
        object : EmailInputUiStateMachine, ScreenStateMachineMock<EmailInputUiProps>(
          "email-input"
        ) {},
      notificationTouchpointDao = notificationTouchpointDao,
      notificationTouchpointF8eClient = notificationTouchpointF8eClient,
      phoneNumberInputUiStateMachine =
        object : PhoneNumberInputUiStateMachine, ScreenStateMachineMock<PhoneNumberInputUiProps>(
          "phone-number-input"
        ) {},
      hardwareAuthUiStateMachine =
        object : HardwareAuthUiStateMachine,
          ScreenStateMachineMock<HardwareAuthUiProps>(
            "hardware-auth"
          ) {},
      verificationCodeInputStateMachine =
        object : VerificationCodeInputStateMachine,
          ScreenStateMachineMock<VerificationCodeInputProps>(
            "verification-code-input"
          ) {},
      uiErrorHintSubmitter = uiErrorHintSubmitter,
      actionSuccessDuration = ActionSuccessDuration(10.milliseconds),
      accountConfigService = accountConfigService,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      usSmsFeatureFlag = usSmsFeatureFlag
    )

  val stateMachine = createStateMachine()

  val props =
    NotificationTouchpointInputAndVerificationProps(
      accountId = FullAccountIdMock,
      touchpointType = PhoneNumber,
      entryPoint =
        OnboardingAndRecovery(
          onSkip = { onSkipCalls += Unit }
        ),
      onClose = { onCloseCalls.add(Unit) },
      onSuccess = { onCloseCalls.add(Unit) }
    )

  beforeTest {
    notificationTouchpointDao.reset()
    notificationTouchpointF8eClient.reset()
    accountConfigService.reset()
    usSmsFeatureFlag.setFlagValue(false)
    telephonyCountryCodeProvider.mockCountryCode = ""
  }

  // Helper function to test both email and phone number through sending the verification code
  suspend fun StateMachineTester<NotificationTouchpointInputAndVerificationProps, ScreenModel>.progressToSendingVerificationCode(
    touchpointType: NotificationTouchpointType,
  ) {
    val code = "1234"
    val phoneNumber = PhoneNumberMock.copy(countryDialingCode = 3)
    val email = EmailFake.copy("abc@123.com")

    notificationTouchpointF8eClient.addTouchpointResult =
      Ok(
        when (touchpointType) {
          PhoneNumber -> PhoneNumberMock.touchpoint()
          Email -> EmailFake.touchpoint()
        }
      )

    // Entering touchpoint
    when (touchpointType) {
      PhoneNumber ->
        awaitBodyMock<PhoneNumberInputUiProps> {
          onSubmitPhoneNumber(phoneNumber) {}
        }
      Email ->
        awaitBodyMock<EmailInputUiProps> {
          onEmailEntered(email) {}
        }
    }

    // Sending touchpoint to server, the loading is happening in the input screen
    val addTouchpointCalls = notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    with(addTouchpointCalls.shouldBeInstanceOf<AddTouchpointParams>().touchpoint) {
      when (touchpointType) {
        PhoneNumber -> shouldBeInstanceOf<PhoneNumberTouchpoint>().value.shouldBe(phoneNumber)
        Email -> shouldBeInstanceOf<EmailTouchpoint>().value.shouldBe(email)
      }
    }

    if (touchpointType == PhoneNumber) {
      phoneNoneCalls.awaitItem()
    }

    // Entering verification code
    awaitBodyMock<VerificationCodeInputProps> {
      onCodeEntered(code)
    }

    // Sending verification code to server
    awaitBody<LoadingSuccessBodyModel> {
      state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
    }
    with(notificationTouchpointF8eClient.verifyTouchpointCalls.awaitItem()) {
      shouldBeTypeOf<VerifyTouchpointParams>()
      touchpointId.shouldBe(touchpointId)
      verificationCode.shouldBe(code)
    }
  }

  test("happy path") {
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(props.copy(touchpointType = touchpointType)) {
        progressToSendingVerificationCode(touchpointType)
        // Sending activation request to server
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          proof.shouldBeNull()
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("needs hardware authorization (W1 hw proof of possession) - new touchpoint uses Set proof type") {
    val hwProofOfPossession = HwFactorProofOfPossession("signed-token")
    val expectedProof = PrivilegedActionProof.HwKeyProof(hwProofOfPossession)
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(
          entryPoint = Settings(fullAccount = FullAccountMock),
          touchpointType = touchpointType
        )
      ) {
        progressToSendingVerificationCode(touchpointType)

        // Activation approval instructions
        awaitBody<FormBodyModel> {
          expectActivationInstructions(
            entryPoint = Settings(fullAccount = FullAccountMock),
            touchpointType = touchpointType
          )
          clickPrimaryButton()
        }

        // Hardware authorization via HardwareAuthUiStateMachine
        awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
          // Verify the correct account and action proof type are passed
          fullAccountId.shouldBe(FullAccountMock.accountId)
          when (touchpointType) {
            PhoneNumber -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryPhone>()
            Email -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryEmail>()
          }

          val errorScreenModel =
            onTokenRefreshError.shouldNotBeNull()
              .invoke(false, Error("test token refresh failure")) {}
          errorScreenModel.body.shouldBeInstanceOf<FormBodyModel>()
            .expectActivationInstructions(
              entryPoint = Settings(fullAccount = FullAccountMock),
              touchpointType = touchpointType
            )
          errorScreenModel.bottomSheetModel.shouldNotBeNull()

          val refreshingScreenModel =
            onTokenRefresh.shouldNotBeNull().invoke()
              .body.shouldBeInstanceOf<FormBodyModel>()
          refreshingScreenModel.expectActivationInstructions(
            entryPoint = Settings(fullAccount = FullAccountMock),
            touchpointType = touchpointType
          )
          refreshingScreenModel.primaryButton.shouldNotBeNull().shouldBeLoading()

          onSuccess(expectedProof)
        }

        // Sending activation request to server
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          touchpointId.shouldBe("123")
          proof.shouldBe(expectedProof)
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("Settings entry point with existing touchpoint uses Set proof type for replacement") {
    val hwProofOfPossession = HwFactorProofOfPossession("signed-token")
    val expectedProof = PrivilegedActionProof.HwKeyProof(hwProofOfPossession)
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(
          entryPoint = Settings(fullAccount = FullAccountMock),
          touchpointType = touchpointType
        )
      ) {
        progressToSendingVerificationCode(touchpointType)

        // Activation approval instructions
        awaitBody<FormBodyModel> {
          expectActivationInstructions(
            entryPoint = Settings(fullAccount = FullAccountMock),
            touchpointType = touchpointType
          )
          clickPrimaryButton()
        }

        // Hardware authorization via HardwareAuthUiStateMachine
        awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
          // Replacing an existing touchpoint must use the SET variants, not ADD
          fullAccountId.shouldBe(FullAccountMock.accountId)
          when (touchpointType) {
            PhoneNumber -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryPhone>()
            Email -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryEmail>()
          }

          onSuccess(expectedProof)
        }

        // Sending activation request to server
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          touchpointId.shouldBe("123")
          proof.shouldBe(expectedProof)
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("needs hardware authorization for W3 onboarding (Recovery entry point)") {
    val hwProofOfPossession = HwFactorProofOfPossession("signed-token")
    val expectedProof = PrivilegedActionProof.HwKeyProof(hwProofOfPossession)
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(
          entryPoint = OnboardingAndRecovery(fullAccount = FullAccountW3Mock),
          touchpointType = touchpointType
        )
      ) {
        progressToSendingVerificationCode(touchpointType)

        // Activation approval instructions
        awaitBody<FormBodyModel> {
          expectActivationInstructions(
            entryPoint = OnboardingAndRecovery(fullAccount = FullAccountW3Mock),
            touchpointType = touchpointType
          )
          clickPrimaryButton()
        }

        // Hardware authorization via HardwareAuthUiStateMachine
        awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
          // Verify the correct account and action proof type are passed
          fullAccountId.shouldBe(FullAccountW3Mock.accountId)
          when (touchpointType) {
            PhoneNumber -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryPhone>()
            Email -> actionProofType.shouldBeInstanceOf<ActionProofType.SetRecoveryEmail>()
          }
          onSuccess(expectedProof)
        }

        // Sending activation request to server
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          touchpointId.shouldBe("123")
          proof.shouldBe(expectedProof)
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("W1 onboarding (Recovery entry point) skips hardware authorization") {
    // Test the flow for both phone and email - W1 should skip HW auth
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(
          entryPoint = OnboardingAndRecovery(fullAccount = FullAccountMock),
          touchpointType = touchpointType
        )
      ) {
        progressToSendingVerificationCode(touchpointType)
        // Should go directly to sending activation (no HW verification)
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          proof.shouldBeNull()
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("Recovery entry point without fullAccount skips hardware authorization") {
    // Test that Recovery without fullAccount (e.g., actual recovery) keeps current behavior
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(
          entryPoint = OnboardingAndRecovery(),
          touchpointType = touchpointType
        )
      ) {
        progressToSendingVerificationCode(touchpointType)
        // Should go directly to sending activation (no HW verification)
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          proof.shouldBeNull()
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("send touchpoint server failure") {
    val onErrorCalls = turbines.create<Unit>("on error server failure calls")
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(F8eError.UnhandledException(UnhandledException(Throwable())))
    stateMachine.test(props) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          onErrorCalls.add(Unit)
        }
      }

      // Sending number to server, the loading and error is happening in the input screen
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // The error should have been sent to the phone input screen to show
      onErrorCalls.awaitItem()
    }
  }

  test("send verification code server failure - connectivity") {
    notificationTouchpointF8eClient.verifyTouchpointResult =
      Err(F8eError.ConnectivityError(NetworkError(Throwable())))
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(props.copy(touchpointType = touchpointType)) {
        progressToSendingVerificationCode(touchpointType)
        // Error screen
        awaitBody<FormBodyModel> {
          with(header.shouldNotBeNull()) {
            when (touchpointType) {
              PhoneNumber -> headline.shouldBe("We couldn’t verify this phone number")
              Email -> headline.shouldBe("We couldn’t verify this email address")
            }
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Make sure you are connected to the internet and try again."
            )
          }
          clickPrimaryButton()
        }

        // Go back to Entering verification code
        awaitBodyMock<VerificationCodeInputProps>()
      }
    }
  }

  test("send verification code server failure - request error") {
    notificationTouchpointF8eClient.verifyTouchpointResult =
      Err(F8eError.UnhandledException(UnhandledException(Throwable())))
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(props.copy(touchpointType = touchpointType)) { // Entering phone number
        progressToSendingVerificationCode(touchpointType)

        // Error screen
        awaitBody<FormBodyModel> {
          with(header.shouldNotBeNull()) {
            when (touchpointType) {
              PhoneNumber -> headline.shouldBe("We couldn’t verify this phone number")
              Email -> headline.shouldBe("We couldn’t verify this email address")
            }
            sublineModel.shouldNotBeNull().string.shouldBe(
              "We are looking into this. Please try again later."
            )
          }
          clickPrimaryButton()
        }

        // Go back to Entering touchpoint
        when (touchpointType) {
          PhoneNumber ->
            awaitBodyMock<PhoneNumberInputUiProps> {
              prefillValue.shouldBe(PhoneNumberMock)
            }
          Email ->
            awaitBodyMock<EmailInputUiProps> {
              previousEmail.shouldBe(EmailFake)
            }
        }
      }
    }
  }

  test("send verification code server failure - code expiration") {
    notificationTouchpointF8eClient.verifyTouchpointResult =
      Err(SpecificClientErrorMock(VerifyTouchpointClientErrorCode.CODE_EXPIRED))
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(props.copy(touchpointType = touchpointType)) {
        progressToSendingVerificationCode(touchpointType)

        // Error screen
        awaitBody<FormBodyModel> {
          with(header.shouldNotBeNull()) {
            when (touchpointType) {
              PhoneNumber -> headline.shouldBe("We couldn’t verify this phone number")
              Email -> headline.shouldBe("We couldn’t verify this email address")
            }
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Your verification code has expired. Please submit your contact details again."
            )
          }
          clickPrimaryButton()
        }

        // Go back to Entering touchpoint
        when (touchpointType) {
          PhoneNumber ->
            awaitBodyMock<PhoneNumberInputUiProps> {
              prefillValue.shouldBe(PhoneNumberMock)
            }
          Email ->
            awaitBodyMock<EmailInputUiProps> {
              previousEmail.shouldBe(EmailFake)
            }
        }
      }
    }
  }

  test("send verification code server failure - code incorrect") {
    notificationTouchpointF8eClient.verifyTouchpointResult =
      Err(SpecificClientErrorMock(VerifyTouchpointClientErrorCode.CODE_MISMATCH))
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(props.copy(touchpointType = touchpointType)) {
        progressToSendingVerificationCode(touchpointType)

        // Error screen
        awaitBody<FormBodyModel> {
          with(header.shouldNotBeNull()) {
            when (touchpointType) {
              PhoneNumber -> headline.shouldBe("We couldn’t verify this phone number")
              Email -> headline.shouldBe("We couldn’t verify this email address")
            }
            sublineModel.shouldNotBeNull().string.shouldBe(
              "The verification code was incorrect. Please try again."
            )
          }
          clickPrimaryButton()
        }

        // Go back to Entering verification code
        awaitBodyMock<VerificationCodeInputProps>()
      }
    }
  }

  test("verify code entry goes back to prefilled touchpoint entry") {
    val phoneNumber = PhoneNumberMock
    notificationTouchpointF8eClient.addTouchpointResult = Ok(phoneNumber.touchpoint())
    stateMachine.test(props) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {}
      }

      // Sending number to server, the loading is happening in the input screen
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitBodyMock<VerificationCodeInputProps> {
        onBack()
      }

      // Back to Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        prefillValue.shouldBe(phoneNumber)
      }

      // Consume the extra phoneNone call
      phoneNoneCalls.awaitItem()
    }
  }

  test("resend code on verify code input screen for sms") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(PhoneNumberMock.touchpoint())
    stateMachine.test(props) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {}
      }

      // Sending number to server, the loading is happening in the input screen
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitBodyMock<VerificationCodeInputProps> {
        onResendCode(ResendCodeCallbacks({}, {}))
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Consume the extra phone-none event from the resend flow
      phoneNoneCalls.awaitItem()
    }
  }

  test("going back from verifying email fills in the email input") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitBodyMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      // Loading, sending email to the server
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      awaitBodyMock<VerificationCodeInputProps> {
        onBack()
      }

      awaitBodyMock<EmailInputUiProps> {
        previousEmail.shouldBe(EmailFake)
      }
    }
  }

  test("properly recover from failure to send email to server") {
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(F8eError.UnhandledException(UnhandledException(Throwable())))
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitBodyMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }

  test("resend code on verify code input screen for email") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitBodyMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      // Loading, sending email to the server
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitBodyMock<VerificationCodeInputProps> {
        onResendCode(ResendCodeCallbacks({}, {}))
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }

  test("recover from invalid country code") {
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))
    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          it.shouldBeTypeOf<F8eError.SpecificClientError<AddTouchpointClientErrorCode>>()
        }
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }

  test("US phone number with UsSmsFeatureFlag disabled triggers phoneNotAvailable") {
    // Set country to US and feature flag to disabled
    telephonyCountryCodeProvider.mockCountryCode = "US"
    usSmsFeatureFlag.setFlagValue(false)

    // Setup error response
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))

    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) { }
      }

      // Should trigger the server call
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Should call phoneNotAvailable since we're in US with flag disabled
      phoneNotAvailableCalls.awaitItem()
    }
  }

  test("US phone number with UsSmsFeatureFlag enabled doesn't trigger phoneNotAvailable") {
    // Set country to US and feature flag to enabled
    telephonyCountryCodeProvider.mockCountryCode = "US"
    usSmsFeatureFlag.setFlagValue(true)

    // Setup error response
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))

    val errorCalls = turbines.create<Unit>("error calls")

    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          errorCalls.add(Unit)
        }
      }

      // Should trigger the server call
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Should still call the error handler
      errorCalls.awaitItem()

      // But should NOT call phoneNotAvailable since feature flag is enabled
      phoneNotAvailableCalls.expectNoEvents()
    }
  }

  test("Non-US phone number error with UsSmsFeatureFlag enabled doesn't trigger phoneNotAvailable") {
    // Set country to non-US (Canada)
    telephonyCountryCodeProvider.mockCountryCode = "CA"
    usSmsFeatureFlag.setFlagValue(true)

    // Setup error response
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))

    val errorCalls = turbines.create<Unit>("error calls enabled")

    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          errorCalls.add(Unit)
        }
      }

      // Should trigger the server call
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Should call the error handler
      errorCalls.awaitItem()

      // But should NOT call phoneNotAvailable since we're not in US
      phoneNotAvailableCalls.expectNoEvents()
    }
  }

  test("Non-US phone number error with UsSmsFeatureFlag disabled doesn't trigger phoneNotAvailable") {
    // Set country to non-US (Canada)
    telephonyCountryCodeProvider.mockCountryCode = "CA"
    usSmsFeatureFlag.setFlagValue(false)

    // Setup error response
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))

    val errorCalls = turbines.create<Unit>("error calls disabled")

    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          errorCalls.add(Unit)
        }
      }

      // Should trigger the server call
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Should call the error handler
      errorCalls.awaitItem()

      // But should NOT call phoneNotAvailable since we're not in US
      phoneNotAvailableCalls.expectNoEvents()
    }
  }

  test("Settings entry point with no stored phone uses Enter style") {
    stateMachine.test(
      props.copy(
        entryPoint = Settings(fullAccount = FullAccountMock),
        touchpointType = PhoneNumber
      )
    ) {
      awaitBodyMock<PhoneNumberInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
    }
  }

  test("Settings entry point with stored phone uses Edit style") {
    notificationTouchpointDao.phoneTouchpointFlow.value = PhoneNumberMock.touchpoint()
    stateMachine.test(
      props.copy(
        entryPoint = Settings(fullAccount = FullAccountMock),
        touchpointType = PhoneNumber
      )
    ) {
      // First emission uses initial null storedTouchpoint; second picks up the stored value
      awaitBodyMock<PhoneNumberInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
      awaitBodyMock<PhoneNumberInputUiProps> {
        dataInputStyle.shouldBe(Edit)
      }
    }
  }

  test("Settings entry point with no stored email uses Enter style") {
    stateMachine.test(
      props.copy(
        entryPoint = Settings(fullAccount = FullAccountMock),
        touchpointType = Email
      )
    ) {
      awaitBodyMock<EmailInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
    }
  }

  test("Settings entry point with stored email uses Edit style") {
    notificationTouchpointDao.emailTouchpointFlow.value = EmailFake.touchpoint()
    stateMachine.test(
      props.copy(
        entryPoint = Settings(fullAccount = FullAccountMock),
        touchpointType = Email
      )
    ) {
      // First emission uses initial null storedTouchpoint; second picks up the stored value
      awaitBodyMock<EmailInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
      awaitBodyMock<EmailInputUiProps> {
        dataInputStyle.shouldBe(Edit)
      }
    }
  }

  test("OnboardingAndRecovery entry point always uses Enter style even with stored phone") {
    notificationTouchpointDao.phoneTouchpointFlow.value = PhoneNumberMock.touchpoint()
    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Initial emission with null storedTouchpoint
      awaitBodyMock<PhoneNumberInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
      // After storedTouchpoint loads, still Enter for onboarding
      awaitBodyMock<PhoneNumberInputUiProps> {
        dataInputStyle.shouldBe(Enter)
      }
    }
  }
})

private fun build.wallet.phonenumber.PhoneNumber.touchpoint(id: String = "123") =
  PhoneNumberTouchpoint(id, this)

private fun build.wallet.email.Email.touchpoint(id: String = "123") = EmailTouchpoint(id, this)

private fun FormBodyModel.expectActivationInstructions(
  entryPoint: NotificationTouchpointInputAndVerificationProps.EntryPoint,
  touchpointType: NotificationTouchpointType,
) {
  with(header.shouldNotBeNull()) {
    when (entryPoint) {
      is Settings -> {
        headline.shouldBe("Approve this change with your Bitkey device")
        primaryButton.shouldNotBeNull().text.shouldBe("Approve")
        when (touchpointType) {
          PhoneNumber ->
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Notifications will be sent to (555) 555-5555"
            )
          Email ->
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Notifications will be sent to asdf@block.xyz"
            )
        }
      }
      is OnboardingAndRecovery -> {
        headline.shouldBe("Confirm details on your Bitkey")
        primaryButton.shouldNotBeNull().text.shouldBe("Continue")
        when (touchpointType) {
          PhoneNumber ->
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Your Bitkey must approve changes to your security settings. Review and approve saving (555) 555-5555 as your recovery phone number."
            )
          Email ->
            sublineModel.shouldNotBeNull().string.shouldBe(
              "Your Bitkey must approve changes to your security settings. Review and approve saving asdf@block.xyz as your recovery email."
            )
        }
      }
    }
  }
}

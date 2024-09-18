package build.wallet.statemachine.notifications

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock.*
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.*
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Onboarding
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.matchers.shouldBeLoading
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class NotificationTouchpointInputAndVerificationUiStateMachineImplTests : FunSpec({

  val onCloseCalls = turbines.create<Unit>("on close calls")
  val onSkipCalls = turbines.create<Unit>("on skip calls")

  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationTouchpointF8eClient = NotificationTouchpointF8eClientMock(turbines::create)

  val stateMachine =
    NotificationTouchpointInputAndVerificationUiStateMachineImpl(
      delayer = ControlledDelayer(),
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
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine, ScreenStateMachineMock<ProofOfPossessionNfcProps>(
          "proof-of-hw"
        ) {},
      verificationCodeInputStateMachine =
        object : VerificationCodeInputStateMachine,
          ScreenStateMachineMock<VerificationCodeInputProps>(
            "verification-code-input"
          ) {},
      uiErrorHintSubmitter = object : UiErrorHintSubmitter {
        override fun phoneNone() {}

        override fun phoneNotAvailable() {}
      }
    )

  val props =
    NotificationTouchpointInputAndVerificationProps(
      accountId = FullAccountIdMock,
      accountConfig = FullAccountConfigMock,
      touchpointType = PhoneNumber,
      entryPoint =
        Onboarding(
          onSkip = { onSkipCalls += Unit },
          skipBottomSheetProvider = { SheetModelMock(it) }
        ),
      onClose = { onCloseCalls.add(Unit) }
    )

  beforeTest {
    notificationTouchpointDao.reset()
    notificationTouchpointF8eClient.reset()
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
        awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
          onSubmitPhoneNumber(phoneNumber) {}
        }
      Email ->
        awaitScreenWithBodyModelMock<EmailInputUiProps> {
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

    // Entering verification code
    awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
      onCodeEntered(code)
    }

    // Sending verification code to server
    awaitScreenWithBody<LoadingSuccessBodyModel> {
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
        awaitScreenWithBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          hwFactorProofOfPossession.shouldBeNull()
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitScreenWithBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
        }

        onCloseCalls.awaitItem()
      }
    }
  }

  test("needs hw proof of possession") {
    val hwProofOfPossession = HwFactorProofOfPossession("signed-token")
    // Test the flow for both phone and email
    listOf(PhoneNumber, Email).forEach { touchpointType ->
      stateMachine.test(
        props.copy(entryPoint = Settings, touchpointType = touchpointType)
      ) {
        progressToSendingVerificationCode(touchpointType)

        // Activation approval instructions
        awaitScreenWithBody<FormBodyModel> {
          expectActivationInstructions(touchpointType)
          clickPrimaryButton()
        }

        // Verifying HW proof
        awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
          val errorScreenModel = onTokenRefreshError.shouldNotBeNull().invoke(false) {}
          errorScreenModel.body.shouldBeInstanceOf<FormBodyModel>()
            .expectActivationInstructions(touchpointType)
          errorScreenModel.bottomSheetModel.shouldNotBeNull()

          val refreshingScreenModel =
            onTokenRefresh.shouldNotBeNull().invoke()
              .body.shouldBeInstanceOf<FormBodyModel>()
          refreshingScreenModel.expectActivationInstructions(touchpointType)
          refreshingScreenModel.primaryButton.shouldNotBeNull().shouldBeLoading()

          (request as Request.HwKeyProof).onSuccess(hwProofOfPossession)
        }

        // Sending activation request to server
        awaitScreenWithBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          touchpointId.shouldBe(touchpointId)
          hwFactorProofOfPossession.shouldBe(hwProofOfPossession)
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitScreenWithBody<LoadingSuccessBodyModel> {
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
      awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
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
        awaitScreenWithBody<FormBodyModel> {
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
        awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
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
        awaitScreenWithBody<FormBodyModel> {
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
            awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
              prefillValue.shouldBe(PhoneNumberMock)
            }
          Email ->
            awaitScreenWithBodyModelMock<EmailInputUiProps> {
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
        awaitScreenWithBody<FormBodyModel> {
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
            awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
              prefillValue.shouldBe(PhoneNumberMock)
            }
          Email ->
            awaitScreenWithBodyModelMock<EmailInputUiProps> {
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
        awaitScreenWithBody<FormBodyModel> {
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
        awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
      }
    }
  }

  test("verify code entry goes back to prefilled touchpoint entry") {
    val phoneNumber = PhoneNumberMock
    notificationTouchpointF8eClient.addTouchpointResult = Ok(phoneNumber.touchpoint())
    stateMachine.test(props) {
      // Entering phone number
      awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {}
      }

      // Sending number to server, the loading is happening in the input screen
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
        onBack()
      }

      // Back to Entering phone number
      awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
        prefillValue.shouldBe(phoneNumber)
      }
    }
  }

  test("resend code on verify code input screen for sms") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(PhoneNumberMock.touchpoint())
    stateMachine.test(props) {
      // Entering phone number
      awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {}
      }

      // Sending number to server, the loading is happening in the input screen
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
        onResendCode(ResendCodeCallbacks({}, {}))
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
      awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
      awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
    }
  }

  test("going back from verifying email fills in the email input") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitScreenWithBodyModelMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      // Loading, sending email to the server
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
        onBack()
      }

      awaitScreenWithBodyModelMock<EmailInputUiProps> {
        previousEmail.shouldBe(EmailFake)
      }
    }
  }

  test("properly recover from failure to send email to server") {
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(F8eError.UnhandledException(UnhandledException(Throwable())))
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitScreenWithBodyModelMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }

  test("resend code on verify code input screen for email") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.test(props.copy(touchpointType = Email)) {
      // Entering email
      awaitScreenWithBodyModelMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      // Loading, sending email to the server
      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()

      // Entering verification code
      awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
        onResendCode(ResendCodeCallbacks({}, {}))
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
      awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
      awaitScreenWithBodyModelMock<VerificationCodeInputProps>()
    }
  }

  test("recover from invalid country code") {
    notificationTouchpointF8eClient.addTouchpointResult =
      Err(SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE))
    stateMachine.test(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitScreenWithBodyModelMock<PhoneNumberInputUiProps> {
        onSubmitPhoneNumber(PhoneNumberMock) {
          it.shouldBeTypeOf<F8eError.SpecificClientError<AddTouchpointClientErrorCode>>()
        }
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }
})

private fun build.wallet.phonenumber.PhoneNumber.touchpoint(id: String = "123") =
  PhoneNumberTouchpoint(id, this)

private fun build.wallet.email.Email.touchpoint(id: String = "123") = EmailTouchpoint(id, this)

private fun FormBodyModel.expectActivationInstructions(
  touchpointType: NotificationTouchpointType,
) {
  with(header.shouldNotBeNull()) {
    headline.shouldBe("Approve this change with your Bitkey device")
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
}

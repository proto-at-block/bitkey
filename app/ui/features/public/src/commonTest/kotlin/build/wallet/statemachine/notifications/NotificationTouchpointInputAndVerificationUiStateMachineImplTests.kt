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
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock.*
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.*
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Onboarding
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

class NotificationTouchpointInputAndVerificationUiStateMachineImplTests : FunSpec({

  val onCloseCalls = turbines.create<Unit>("on close calls")
  val onSkipCalls = turbines.create<Unit>("on skip calls")

  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationTouchpointF8eClient = NotificationTouchpointF8eClientMock(turbines::create)
  val accountConfigService = AccountConfigServiceFake()

  val stateMachine =
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
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine,
          ScreenStateMachineMock<ProofOfPossessionNfcProps>(
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
      },
      actionSuccessDuration = ActionSuccessDuration(10.milliseconds),
      accountConfigService = accountConfigService
    )

  val props =
    NotificationTouchpointInputAndVerificationProps(
      accountId = FullAccountIdMock,
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
    accountConfigService.reset()
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
      stateMachine.testWithVirtualTime(props.copy(touchpointType = touchpointType)) {
        progressToSendingVerificationCode(touchpointType)
        // Sending activation request to server
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          hwFactorProofOfPossession.shouldBeNull()
        }

        notificationTouchpointDao.storeTouchpointCalls.awaitItem()

        awaitBody<LoadingSuccessBodyModel> {
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
      stateMachine.testWithVirtualTime(
        props.copy(entryPoint = Settings, touchpointType = touchpointType)
      ) {
        progressToSendingVerificationCode(touchpointType)

        // Activation approval instructions
        awaitBody<FormBodyModel> {
          expectActivationInstructions(touchpointType)
          clickPrimaryButton()
        }

        // Verifying HW proof
        awaitBodyMock<ProofOfPossessionNfcProps> {
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
        awaitBody<LoadingSuccessBodyModel> {
          state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
        }
        with(notificationTouchpointF8eClient.activateTouchpointCalls.awaitItem()) {
          shouldBeTypeOf<ActivateTouchpointParams>()
          touchpointId.shouldBe(touchpointId)
          hwFactorProofOfPossession.shouldBe(hwProofOfPossession)
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
    stateMachine.testWithVirtualTime(props) {
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
      stateMachine.testWithVirtualTime(props.copy(touchpointType = touchpointType)) {
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
      stateMachine.testWithVirtualTime(props.copy(touchpointType = touchpointType)) { // Entering phone number
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
      stateMachine.testWithVirtualTime(props.copy(touchpointType = touchpointType)) {
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
      stateMachine.testWithVirtualTime(props.copy(touchpointType = touchpointType)) {
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
    stateMachine.testWithVirtualTime(props) {
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
    }
  }

  test("resend code on verify code input screen for sms") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(PhoneNumberMock.touchpoint())
    stateMachine.testWithVirtualTime(props) {
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
    }
  }

  test("going back from verifying email fills in the email input") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.testWithVirtualTime(props.copy(touchpointType = Email)) {
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
    stateMachine.testWithVirtualTime(props.copy(touchpointType = Email)) {
      // Entering email
      awaitBodyMock<EmailInputUiProps> {
        onEmailEntered(EmailFake) {}
      }

      notificationTouchpointF8eClient.addTouchpointCalls.awaitItem()
    }
  }

  test("resend code on verify code input screen for email") {
    notificationTouchpointF8eClient.addTouchpointResult = Ok(EmailFake.touchpoint())
    stateMachine.testWithVirtualTime(props.copy(touchpointType = Email)) {
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
    stateMachine.testWithVirtualTime(props.copy(touchpointType = PhoneNumber)) {
      // Entering phone number
      awaitBodyMock<PhoneNumberInputUiProps> {
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

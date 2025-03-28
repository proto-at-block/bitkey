package build.wallet.statemachine.recovery.verification

import app.cash.turbine.ReceiveTurbine
import bitkey.f8e.error.F8eError
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationTouchpointServiceFake
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.VerificationCodeInputProps
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RecoveryNotificationVerificationUiStateMachineImplTests : FunSpec({

  val notificationTouchpointService = NotificationTouchpointServiceFake()

  val stateMachine =
    RecoveryNotificationVerificationUiStateMachineImpl(
      verificationCodeInputStateMachine =
        object : VerificationCodeInputStateMachine,
          ScreenStateMachineMock<VerificationCodeInputProps>(
            "verification-code-input"
          ) {},
      notificationTouchpointService = notificationTouchpointService
    )

  val propsOnCompleteCalls = turbines.create<Unit>("props onComplete calls")
  val propsOnRollbackCalls = turbines.create<Unit>("props onRollback calls")

  val props = RecoveryNotificationVerificationUiProps(
    fullAccountId = FullAccountIdMock,
    localLostFactor = PhysicalFactor.Hardware,
    hwFactorProofOfPossession = HwFactorProofOfPossession(""),
    onRollback = { propsOnRollbackCalls.add(Unit) },
    onComplete = { propsOnCompleteCalls.add(Unit) }
  )

  beforeTest {
    notificationTouchpointService.reset()
  }

  test("happy path - sms") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onCodeEntered("123")
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      propsOnCompleteCalls.awaitItem()
    }
  }

  test("happy path - email") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("Email")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onCodeEntered("123")
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      propsOnCompleteCalls.awaitItem()
    }
  }

  test("loading notifications failure") {
    notificationTouchpointService.syncNotificationTouchpointsResult =
      Err(HttpError.NetworkError(Throwable()))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("We couldn’t load verification for recovery")
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Retry")
          onClick()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("We couldn’t load verification for recovery")
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Back")
          onClick() // Rollback
        }
      }

      propsOnRollbackCalls.awaitItem()
    }
  }

  test("ChoosingNotificationTouchpointUiState onBack") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      propsOnRollbackCalls.awaitItem()
    }
  }

  test("send code error - rollback and retry") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))
    notificationTouchpointService.sendVerificationCodeToTouchpointResult =
      Err(HttpError.NetworkError(Throwable()))
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldNotBeNull()
          .shouldBe("We couldn’t send a verification code")
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Back")
          onClick()
        }
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        notificationTouchpointService.sendVerificationCodeToTouchpointResult = Ok(Unit)

        header.shouldNotBeNull().headline.shouldNotBeNull()
          .shouldBe("We couldn’t send a verification code")
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Retry")
          onClick()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onBack.invoke()
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldNotBeNull().shouldBe("Verification Required")
      }
    }
  }

  test("verify code error - rollback connectivity error") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))
    notificationTouchpointService.verifyCodeResult =
      Err(F8eError.ConnectivityError(HttpError.NetworkError(Throwable())))
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onCodeEntered("123")
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldNotBeNull()
          .shouldBe("We couldn’t verify the entered code")

        // Click rollback
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Back")
          onClick()
        }
      }

      awaitBodyMock<VerificationCodeInputProps> {
      }
    }
  }

  test("verify code error - rollback non-connectivity error") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))
    notificationTouchpointService.verifyCodeResult =
      Err(F8eError.UnhandledError(HttpError.NetworkError(Throwable())))
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onCodeEntered("123")
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldNotBeNull()
          .shouldBe("We couldn’t verify the entered code")

        // Click rollback
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Back")
          onClick()
        }
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldNotBeNull().shouldBe("Verification Required")
      }
    }
  }

  test("verify code error - retry") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointService.syncNotificationTouchpointsResult = Ok(listOf(sms, email))
    notificationTouchpointService.verifyCodeResult =
      Err(F8eError.UnhandledError(HttpError.NetworkError(Throwable())))
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitAndSelectTouchpoint("SMS")

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBodyMock<VerificationCodeInputProps> {
        onCodeEntered("123")
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitBody<FormBodyModel> {
        notificationTouchpointService.verifyCodeResult = Ok(Unit)

        header.shouldNotBeNull().headline.shouldNotBeNull()
          .shouldBe("We couldn’t verify the entered code")

        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Retry")
          onClick()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      propsOnCompleteCalls.awaitItem()
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.awaitAndSelectTouchpoint(touchpointName: String) =
  apply {
    awaitBody<FormBodyModel> {
      header.shouldNotBeNull().headline.shouldNotBeNull().shouldBe("Verification Required")

      val listModel =
        mainContentList.first()
          .shouldBeInstanceOf<FormMainContentModel.ListGroup>().listGroupModel

      listModel.items.find {
        it.title == touchpointName
      }.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
    }
  }

package build.wallet.statemachine.core.input

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_RESEND
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_RESEND_SKIP_FOR_NOW
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.Email
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Hidden
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Showing
import build.wallet.statemachine.core.input.VerificationCodeInputProps.ResendCodeCallbacks
import build.wallet.statemachine.core.test
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class VerificationCodeInputStateMachineImplTests : FunSpec({

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onCodeEnteredCalls = turbines.create<Unit>("on code entered calls")
  val onResendCodeCalls = turbines.create<ResendCodeCallbacks>("on resend code calls")

  val clock = ClockFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val stateMachine =
    VerificationCodeInputStateMachineImpl(
      clock = clock,
      durationFormatter = DurationFormatterFake(),
      eventTracker = eventTracker
    )

  val props =
    VerificationCodeInputProps(
      title = "Some title",
      subtitle = "Some subtitle",
      expectedCodeLength = 6,
      notificationTouchpoint =
        PhoneNumberTouchpoint(
          touchpointId = "id",
          value = PhoneNumberMock
        ),
      onBack = { onBackCalls.add(Unit) },
      onCodeEntered = { onCodeEnteredCalls.add(Unit) },
      onResendCode = { resendCodeCallbacks -> onResendCodeCalls.add(resendCodeCallbacks) },
      skipBottomSheetProvider = { SheetModelMock(it) },
      screenId = null
    )

  beforeTest {
    clock.reset()
  }

  test("initial state") {
    stateMachine.test(props) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe(props.title)
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          fieldModel.placeholderText.shouldBe("Verification code")
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldBeNull()
          resendCodeContent.shouldBeInstanceOf<Text>()
        }
      }
    }
  }

  test("onValueChange") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          // Change to less than expected length
          fieldModel.onValueChange("1".repeat(props.expectedCodeLength - 1))
        }
      }

      // Updated value
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          fieldModel.value.shouldBe("1".repeat(props.expectedCodeLength - 1))
          // Change to expected length
          fieldModel.onValueChange("1".repeat(props.expectedCodeLength))
        }
      }

      // Updated value
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          fieldModel.value.shouldBe("1".repeat(props.expectedCodeLength))
        }
      }

      onCodeEnteredCalls.awaitItem()
    }
  }

  test("onValueChange with more than expected length") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          // Change to less than expected length
          fieldModel.onValueChange("1".repeat(props.expectedCodeLength + 5))
        }
      }

      // Updated value
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeTypeOf<VerificationCodeInput>()) {
          fieldModel.value.shouldBe("1".repeat(props.expectedCodeLength))
        }
      }

      onCodeEnteredCalls.awaitItem()
    }
  }

  test("resend code resets content after success") {
    stateMachine.test(props) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel>()

      clock.advanceBy(30.seconds)

      // Resend code available
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.onClick()
      }

      val resendCodeCallbacks = onResendCodeCalls.awaitItem()

      // Showing skip for now
      awaitScreenWithBody<FormBodyModel>()

      // Resend code loading
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.isLoading.shouldBeTrue()
      }

      resendCodeCallbacks.onSuccess()

      // Resend code blocked
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Text>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND))
    }
  }

  test("resend code shows error after error") {
    stateMachine.test(props) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel>()

      clock.advanceBy(30.seconds)

      // Resend code available
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.onClick()
      }

      val resendCodeCallbacks = onResendCodeCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND))

      // Showing skip for now
      awaitScreenWithBody<FormBodyModel>()

      // Resend code loading
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.isLoading.shouldBeTrue()
      }

      resendCodeCallbacks.onError(false)

      // Showing error
      awaitItem().bottomSheetModel.shouldNotBeNull()
    }
  }

  test("skip after resend code when skip sheet provided") {
    stateMachine.test(props) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .skipForNowContent.shouldBeInstanceOf<Hidden>()
      }

      clock.advanceBy(30.seconds)

      // Resend code available
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.onClick()
      }

      onResendCodeCalls.awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND))

      // Showing skip for now
      awaitScreenWithBody<FormBodyModel>()

      // Resend code loading
      awaitScreenWithBody<FormBodyModel> {
        val skipForNowContent =
          mainContentList.first()
            .shouldBeTypeOf<VerificationCodeInput>()
            .skipForNowContent.shouldBeInstanceOf<Showing>()

        skipForNowContent.text.shouldBe("Can’t receive the code?")
        skipForNowContent.button.text.shouldBe("Skip for now")
        skipForNowContent.button.onClick()
      }
      awaitScreenWithBody<FormBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND_SKIP_FOR_NOW)
      )
    }
  }

  test("no skip showing after resend code when skip sheet not provided") {
    stateMachine.test(props.copy(skipBottomSheetProvider = null)) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .skipForNowContent.shouldBeInstanceOf<Hidden>()
      }

      clock.advanceBy(30.seconds)

      // Resend code available
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND))

      onResendCodeCalls.awaitItem()

      // Resend code loading
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .skipForNowContent.shouldBeInstanceOf<Hidden>()
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("skip for now - skip sheet - go back") {
    stateMachine.test(props) {
      // Resend code blocked
      awaitScreenWithBody<FormBodyModel>()
      clock.advanceBy(30.seconds)
      // Resend code available
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .resendCodeContent.shouldBeInstanceOf<Button>()
          .value.onClick()
      }
      onResendCodeCalls.awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND))

      // Showing skip for now
      awaitScreenWithBody<FormBodyModel>()

      // Resend code loading
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first()
          .shouldBeTypeOf<VerificationCodeInput>()
          .skipForNowContent.shouldBeInstanceOf<Showing>()
          .button.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PHONE_NUMBER_RESEND_SKIP_FOR_NOW)
      )

      // Showing skip sheet
      awaitItem().bottomSheetModel
        .shouldNotBeNull()
        .onClosed()

      // Dismissed skip sheet
      awaitItem().bottomSheetModel
        .shouldBeNull()
    }
  }

  test("email explainer is shown with email touchpoint") {
    stateMachine.test(
      props.copy(
        notificationTouchpoint = EmailTouchpoint(touchpointId = "", value = Email("asdf@block.xyz"))
      )
    ) {
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList[1].shouldBeInstanceOf<Explainer>()) {
          items[0].body.shouldBeInstanceOf<StringModel>()
            .string.shouldBe("If the code doesn’t arrive, please check your spam folder.")
        }
      }
    }
  }
})

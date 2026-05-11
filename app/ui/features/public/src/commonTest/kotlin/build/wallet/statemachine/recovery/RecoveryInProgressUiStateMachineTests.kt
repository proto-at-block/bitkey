package build.wallet.statemachine.recovery

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormMainContentModel.Timer.Display.RemainingDuration
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiProps
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiStateMachine
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RecoveryInProgressUiStateMachineTests : FunSpec({
  val clock = ClockFake(Instant.DISTANT_PAST)
  val stateMachine =
    RecoveryInProgressUiStateMachineImpl(
      completingRecoveryUiStateMachine =
        object : CompletingRecoveryUiStateMachine,
          ScreenStateMachineMock<CompletingRecoveryUiProps>(
            "completing-recovery"
          ) {},
      hardwareAuthUiStateMachine =
        object : HardwareAuthUiStateMachine,
          ScreenStateMachineMock<HardwareAuthUiProps>(
            "hardware-auth"
          ) {},
      clock = clock,
      eventTracker = EventTrackerMock(turbines::create),
      recoveryNotificationVerificationUiStateMachine =
        object : RecoveryNotificationVerificationUiStateMachine,
          ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
            "recovery-notification-verification"
          ) {},
      remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.milliseconds)
    )

  val failedToCancelDoneCalls = turbines.create<Unit>("FailedToCancelRecoveryData back calls")

  val failedCancelErrorProps =
    RecoveryInProgressUiProps(
      presentationStyle = ScreenPresentationStyle.Root,
      recoveryInProgressData =
        RecoveryInProgressData.FailedToCancelRecoveryData(
          recoveredFactor = PhysicalFactor.App,
          isNetworkError = false,
          onAcknowledge = { failedToCancelDoneCalls.add(Unit) },
          cause = Error()
        ),
      onExit = {}
    )

  val failedCancelErrorPropsNetworkError =
    RecoveryInProgressUiProps(
      presentationStyle = ScreenPresentationStyle.Root,
      recoveryInProgressData =
        RecoveryInProgressData.FailedToCancelRecoveryData(
          recoveredFactor = PhysicalFactor.App,
          isNetworkError = true,
          onAcknowledge = { failedToCancelDoneCalls.add(Unit) },
          cause = Error()
        ),
      onExit = {}
    )

  test("FailedToCancelRecoveryData model") {
    stateMachine.test(failedCancelErrorProps) {
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
        failedToCancelDoneCalls.awaitItem()

        onBack.shouldNotBeNull().invoke()
        failedToCancelDoneCalls.awaitItem()

        header?.sublineModel?.string.shouldBe(
          "We are looking into this. Please try again later."
        )
      }
    }
  }

  test("waiting app recovery timer requests seconds during final displayed minute") {
    val now = Instant.fromEpochSeconds(0)
    clock.now = now

    stateMachine.test(
      RecoveryInProgressUiProps(
        presentationStyle = ScreenPresentationStyle.Root,
        recoveryInProgressData = WaitingForRecoveryDelayPeriodData(
          factorToRecover = App,
          delayPeriodStartTime = now - 1.seconds,
          delayPeriodEndTime = now + 65.seconds,
          cancel = {}
        )
      )
    ) {
      awaitBody<AppDelayNotifyInProgressBodyModel> {
        timerModel.timerRemainingSeconds.shouldBe(65)
        timerModel.display
          .shouldBeInstanceOf<RemainingDuration>()
          .enableLocalSecondsTick.shouldBe(true)
      }
    }
  }

  test("FailedToCancelRecoveryData networkError model") {
    stateMachine.test(failedCancelErrorPropsNetworkError) {
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
        failedToCancelDoneCalls.awaitItem()

        onBack.shouldNotBeNull().invoke()
        failedToCancelDoneCalls.awaitItem()

        header?.sublineModel?.string.shouldBe(
          "Make sure you are connected to the internet and try again."
        )
      }
    }
  }
})

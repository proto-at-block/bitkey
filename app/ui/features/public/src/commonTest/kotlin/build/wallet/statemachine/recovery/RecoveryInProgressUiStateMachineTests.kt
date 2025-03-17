package build.wallet.statemachine.recovery

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiProps
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiStateMachine
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class RecoveryInProgressUiStateMachineTests : FunSpec({
  val stateMachine =
    RecoveryInProgressUiStateMachineImpl(
      completingRecoveryUiStateMachine =
        object : CompletingRecoveryUiStateMachine,
          ScreenStateMachineMock<CompletingRecoveryUiProps>(
            "completing-recovery"
          ) {},
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine,
          ScreenStateMachineMock<ProofOfPossessionNfcProps>(
            "proof-of-possession-nfc"
          ) {},
      durationFormatter = DurationFormatterFake(),
      clock = ClockFake(Instant.DISTANT_PAST),
      eventTracker = EventTrackerMock(turbines::create),
      recoveryNotificationVerificationUiStateMachine =
        object : RecoveryNotificationVerificationUiStateMachine,
          ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
            "recovery-notification-verification"
          ) {}
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
    stateMachine.testWithVirtualTime(failedCancelErrorProps) {
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

  test("FailedToCancelRecoveryData networkError model") {
    stateMachine.testWithVirtualTime(failedCancelErrorPropsNetworkError) {
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

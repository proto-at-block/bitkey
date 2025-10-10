package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.RotatingAuthKeysWithF8eData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachineImpl
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.ui.matchers.shouldHaveSubtitle
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.matchers.shouldNotHaveSubtitle
import build.wallet.statemachine.ui.robots.click
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class HardwareRecoveryStatusCardUiStateMachineImplTests : FunSpec({
  val dsm = object : LostHardwareRecoveryDataStateMachine,
    StateMachineMock<LostHardwareRecoveryProps, LostHardwareRecoveryData>(
      LostHardwareRecoveryData.LostHardwareRecoveryNotStarted
    ) {}

  val clock = ClockFake()
  val stateMachine = HardwareRecoveryStatusCardUiStateMachineImpl(
    clock = clock,
    durationFormatter = DurationFormatterFake(),
    lostHardwareRecoveryDataStateMachine = dsm,
    recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create),
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.seconds)
  )

  val onClickCalls = turbines.create<Unit>("on click calls")

  val props = HardwareRecoveryStatusCardUiProps(
    account = FullAccountMock,
    onClick = {
      onClickCalls += Unit
    }
  )

  test("null for InitiatingLostHardwareRecoveryData") {
    dsm.emitModel(
      LostHardwareRecoveryInProgressData(
        RotatingAuthKeysWithF8eData(Hardware)
      )
    )
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("null for other UndergoingRecoveryData") {
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("ready to complete") {
    dsm.emitModel(
      LostHardwareRecoveryInProgressData(
        ReadyToCompleteRecoveryData(
          canCancelRecovery = true,
          physicalFactor = Hardware,
          startComplete = { },
          cancel = { }
        )
      )
    )
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement Ready")
        .shouldNotHaveSubtitle()
        .click()
      onClickCalls.awaitItem()
    }
  }

  test("delay in progress") {
    dsm.emitModel(
      LostHardwareRecoveryInProgressData(
        WaitingForRecoveryDelayPeriodData(
          factorToRecover = Hardware,
          delayPeriodStartTime = Instant.DISTANT_PAST,
          delayPeriodEndTime = Instant.DISTANT_PAST,
          cancel = { },
          retryCloudRecovery = null
        )
      )
    )

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement pending...")
        .shouldHaveSubtitle("0s")
        .click()
      onClickCalls.awaitItem()
    }
  }
})

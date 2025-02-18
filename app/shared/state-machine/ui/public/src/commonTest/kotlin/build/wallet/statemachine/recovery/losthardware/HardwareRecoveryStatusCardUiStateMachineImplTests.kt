package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.RotatingAuthKeysWithF8eData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachineImpl
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

class HardwareRecoveryStatusCardUiStateMachineImplTests : FunSpec({

  val clock = ClockFake()
  val stateMachine =
    HardwareRecoveryStatusCardUiStateMachineImpl(
      clock = clock,
      durationFormatter = DurationFormatterFake()
    )

  val onClickCalls = turbines.create<Unit>("on click calls")

  val props =
    HardwareRecoveryStatusCardUiProps(
      lostHardwareRecoveryData =
        AwaitingNewHardwareData(
          newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
          addHardwareKeys = { _, _, _ -> }
        ),
      onClick = {
        onClickCalls += Unit
      }
    )

  test("null for InitiatingLostHardwareRecoveryData") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("null for other UndergoingRecoveryData") {
    stateMachine.testWithVirtualTime(
      props.copy(
        lostHardwareRecoveryData =
          LostHardwareRecoveryInProgressData(
            RotatingAuthKeysWithF8eData(Hardware)
          )
      )
    ) {
      awaitItem().shouldBeNull()
    }
  }

  test("ready to complete") {
    stateMachine.testWithVirtualTime(
      props.copy(
        lostHardwareRecoveryData =
          LostHardwareRecoveryInProgressData(
            ReadyToCompleteRecoveryData(
              canCancelRecovery = true,
              physicalFactor = Hardware,
              startComplete = { },
              cancel = { }
            )
          )
      )
    ) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement Ready")
        .shouldNotHaveSubtitle()
        .click()
      onClickCalls.awaitItem()
    }
  }

  test("delay in progress") {
    stateMachine.testWithVirtualTime(
      props.copy(
        lostHardwareRecoveryData =
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
    ) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement pending...")
        .shouldHaveSubtitle("0s")
        .click()
      onClickCalls.awaitItem()
    }
  }
})

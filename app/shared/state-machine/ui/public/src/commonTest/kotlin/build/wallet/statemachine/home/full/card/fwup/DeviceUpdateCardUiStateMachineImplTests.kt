package build.wallet.statemachine.home.full.card.fwup

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_TAP_FWUP_CARD
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareDataPendingUpdateMock
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DeviceUpdateCardUiStateMachineImplTests : FunSpec({

  val onUpdateCalls = turbines.create<Unit>("on update calls")

  val eventTracker = EventTrackerMock(turbines::create)

  val propsWithoutData =
    DeviceUpdateCardUiProps(
      firmwareData = FirmwareDataUpToDateMock,
      onUpdateDevice = {}
    )

  val propsWithData =
    DeviceUpdateCardUiProps(
      firmwareData = FirmwareDataPendingUpdateMock,
      onUpdateDevice = { onUpdateCalls += Unit }
    )

  val stateMachine =
    DeviceUpdateCardUiStateMachineImpl(
      eventTracker = eventTracker
    )

  test("null is returned when props FwupData is null") {
    stateMachine.test(propsWithoutData) {
      awaitItem().shouldBeNull()
    }
  }

  test("card is returned") {
    stateMachine.test(propsWithData) {
      awaitItem().shouldNotBeNull()
    }
  }

  test("event is logged when click handler is invoked") {
    stateMachine.test(propsWithData) {
      awaitItem().shouldNotBeNull().onClick?.invoke()

      onUpdateCalls.awaitItem().shouldBe(Unit)
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))
    }
  }
})

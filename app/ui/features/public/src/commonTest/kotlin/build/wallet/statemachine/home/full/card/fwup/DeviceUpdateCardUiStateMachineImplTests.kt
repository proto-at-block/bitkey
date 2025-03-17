package build.wallet.statemachine.home.full.card.fwup

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_TAP_FWUP_CARD
import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DeviceUpdateCardUiStateMachineImplTests : FunSpec({

  val onUpdateCalls = turbines.create<Unit>("on update calls")

  val eventTracker = EventTrackerMock(turbines::create)

  val props = DeviceUpdateCardUiProps(
    onUpdateDevice = { onUpdateCalls += Unit }
  )

  val firmwareDataService = FirmwareDataServiceFake()

  val stateMachine =
    DeviceUpdateCardUiStateMachineImpl(
      eventTracker = eventTracker,
      firmwareDataService = firmwareDataService
    )

  beforeTest {
    firmwareDataService.reset()
  }

  test("null is returned when fw is up to date") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("card is returned") {
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock

    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldNotBeNull()
    }
  }

  test("event is logged when click handler is invoked") {
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock

    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldNotBeNull().onClick?.invoke()

      onUpdateCalls.awaitItem().shouldBe(Unit)
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))
    }
  }
})

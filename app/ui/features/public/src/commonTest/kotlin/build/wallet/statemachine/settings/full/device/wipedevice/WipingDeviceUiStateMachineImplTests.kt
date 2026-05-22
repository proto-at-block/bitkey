package build.wallet.statemachine.settings.full.device.wipedevice

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.processing.WipingDeviceProgressProps
import build.wallet.statemachine.settings.full.device.wipedevice.processing.WipingDeviceProgressUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class WipingDeviceUiStateMachineImplTests : FunSpec({
  val wipingDeviceIntroUiStateMachine =
    object : ScreenStateMachineMock<WipingDeviceIntroProps>("wipe-device-intro"),
      WipingDeviceIntroUiStateMachine {}
  val wipingDeviceConfirmationUiStateMachine =
    object : ScreenStateMachineMock<WipingDeviceConfirmationProps>("wipe-device-confirmation"),
      WipingDeviceConfirmationUiStateMachine {}
  val wipingDeviceProgressUiStateMachine =
    object : ScreenStateMachineMock<WipingDeviceProgressProps>("wipe-device-progress"),
      WipingDeviceProgressUiStateMachine {}

  val stateMachine = WipingDeviceUiStateMachineImpl(
    wipingDeviceIntroUiStateMachine = wipingDeviceIntroUiStateMachine,
    wipingDeviceConfirmationUiStateMachine = wipingDeviceConfirmationUiStateMachine,
    wipingDeviceProgressUiStateMachine = wipingDeviceProgressUiStateMachine
  )

  val onSuccessCalls = turbines.create<Unit>("on success calls")

  val props = WipingDeviceProps(
    onBack = {},
    onSuccess = { onSuccessCalls += Unit },
    fullAccount = null
  )

  test("shows wiped confirmation text at top without video content after wipe completes") {
    stateMachine.test(props) {
      awaitBodyMock<WipingDeviceIntroProps>(id = wipingDeviceIntroUiStateMachine.id) {
        onDeviceConfirmed(true, WipeContext.Default)
      }
      awaitBodyMock<WipingDeviceConfirmationProps>(
        id = wipingDeviceConfirmationUiStateMachine.id
      ) {
        onWipeDevice()
      }
      awaitBodyMock<WipingDeviceProgressProps>(id = wipingDeviceProgressUiStateMachine.id) {
        onCompleted()
      }
      awaitBody<SuccessBodyModel> {
        id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_SUCCESS)
        title.shouldBe("Your Bitkey device is now wiped")
        message.shouldBe("Your device has been wiped and can now be safely discarded or passed on.")
        mainContentList.shouldBeEmpty()
        val button = primaryButton.shouldNotBeNull()
        button.text.shouldBe("Done")
        button.onClick()
      }

      onSuccessCalls.awaitItem().shouldBe(Unit)
    }
  }
})

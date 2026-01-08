package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HardwareConfirmationUiStateMachineImplTests : FunSpec({

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = HardwareConfirmationUiStateMachineImpl()

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onConfirmCalls = turbines.create<Unit>("on confirm calls")

  val props =
    HardwareConfirmationUiProps(onBack = {
      onBackCalls.add(Unit)
    }, onConfirm = { onConfirmCalls.add(Unit) })

  beforeTest {
    inAppBrowserNavigator.reset()
  }

  test("shows confirmation screen initially") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onConfirm.shouldNotBeNull()
        onBack.shouldNotBeNull()
      }
    }
  }

  test("clicking Yes, send calls onConfirm") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onConfirm()
      }

      onConfirmCalls.awaitItem()
    }
  }

  test("clicking No, cancel shows cancellation screen") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onBack()
      }

      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Done")
        }
      }
    }
  }

  test("clicking Done on cancellation screen calls onBack") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onBack()
      }

      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onBackCalls.awaitItem()
    }
  }

  test("full cancellation flow") {
    stateMachine.test(props) {
      // Start at confirmation
      awaitBody<HardwareConfirmationScreenModel> {
        onBack()
      }

      // Show cancellation screen
      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Done")
          onClick()
        }
      }

      // Verify callback was invoked
      onBackCalls.awaitItem()
    }
  }
})

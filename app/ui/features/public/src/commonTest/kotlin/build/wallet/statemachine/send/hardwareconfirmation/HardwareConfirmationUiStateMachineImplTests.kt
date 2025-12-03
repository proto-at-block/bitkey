package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HardwareConfirmationUiStateMachineImplTests : FunSpec({

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = HardwareConfirmationUiStateMachineImpl(
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onConfirmCalls = turbines.create<Unit>("on confirm calls")

  val props = HardwareConfirmationUiProps(
    onBack = { onBackCalls.add(Unit) },
    onConfirm = { onConfirmCalls.add(Unit) }
  )

  beforeTest {
    inAppBrowserNavigator.reset()
  }

  test("shows confirmation screen initially") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onSend.shouldNotBeNull()
        onLearnMore.shouldNotBeNull()
        onBack.shouldNotBeNull()
      }
    }
  }

  test("clicking Learn more and closing browser returns to confirmation") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onLearnMore()
      }

      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem().shouldBe(
        HardwareConfirmationUiStateMachine.HARDWARE_CONFIRMATION_LEARN_MORE_URL
      )

      // Simulate browser close by invoking the callback
      inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()

      awaitBody<HardwareConfirmationScreenModel> {
        // Back at confirmation screen
        onSend.shouldNotBeNull()
      }
    }
  }

  test("clicking Yes, send calls onConfirm") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onSend()
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

package build.wallet.statemachine.settings.full.device.resetdevice

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiStateMachineImpl
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class ResettingDeviceConfirmationUiStateMachineImplTests : FunSpec({
  val stateMachine = ResettingDeviceConfirmationUiStateMachineImpl()

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onConfirmResetDeviceCalls = turbines.create<Unit>("on confirm reset device calls")

  val props = ResettingDeviceConfirmationProps(
    onBack = { onBackCalls += Unit },
    onConfirmResetDevice = { onConfirmResetDeviceCalls += Unit }
  )

  test("onBack calls") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        val icon = toolbar.shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("test content and unchecked checkboxes") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          header.shouldNotBeNull().apply {
            headline.shouldBe("Confirm to continue")
            sublineModel.shouldNotBeNull().string.shouldBe("Please read and agree to the following before you continue.")
          }

          mainContentList[0].apply {
            shouldBeInstanceOf<FormMainContentModel.ListGroup>()
            listGroupModel.items[0].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
              iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon == Icon.SmallIconCheckbox
            }
            listGroupModel.items[0].title.shouldBe("Resetting my device means that I will not be able to use it to verify any future transfers or security changes.")

            listGroupModel.items[1].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
              iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon == Icon.SmallIconCheckbox
            }
            listGroupModel.items[1].title.shouldBe("Resetting my device means that I will not be able to use it to set up my wallet on a new phone.")
          }
        }

        primaryButton.shouldNotBeNull().apply {
          shouldBeInstanceOf<ButtonModel>()
          text.shouldBe("Reset device")
        }
      }
    }
  }

  test("reset device button is disabled if not all messages are checked") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()

          // Check only the first message
          listGroupModel.items[0].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().apply {
            onClick.shouldNotBeNull().invoke()
          }
          awaitItem()
        }

        // Ensure that the primary button state is updated after the first item is clicked
        primaryButton.shouldNotBeNull().apply {
          isEnabled.shouldBeFalse()
        }
      }
    }
  }
})

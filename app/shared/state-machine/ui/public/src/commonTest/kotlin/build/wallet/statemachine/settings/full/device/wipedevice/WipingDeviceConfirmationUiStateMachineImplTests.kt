package build.wallet.statemachine.settings.full.device.wipedevice

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiStateMachineImpl
import build.wallet.statemachine.ui.matchers.shouldHaveId
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class WipingDeviceConfirmationUiStateMachineImplTests : FunSpec({
  val stateMachine = WipingDeviceConfirmationUiStateMachineImpl(
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "wiping device nfc"
      ) {},
    firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onConfirmWipeDeviceCalls = turbines.create<Unit>("on confirm wipe device calls")

  val props = WipingDeviceConfirmationProps(
    onBack = { onBackCalls += Unit },
    onWipeDevice = { onConfirmWipeDeviceCalls += Unit },
    isDevicePaired = true,
    isHardwareFake = true
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
            sublineModel.shouldNotBeNull().string.shouldBe("I understand that:")
          }

          mainContentList[0].apply {
            shouldBeInstanceOf<FormMainContentModel.ListGroup>()
            listGroupModel.items[0].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
              iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.apply {
                shouldBe(Icon.SmallIconCheckbox)
              }
            }
            listGroupModel.items[0].title.shouldBe("This device can no longer be used to access the funds in my Bitkey wallet.")

            listGroupModel.items[1].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
              iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.apply {
                shouldBe(Icon.SmallIconCheckbox)
              }
            }
            listGroupModel.items[1].title.shouldBe("This device can no longer be used to recover access to my Bitkey wallet if I lose my phone.")
          }
        }

        primaryButton.shouldNotBeNull().apply {
          shouldBeInstanceOf<ButtonModel>()
          text.shouldBe("Wipe device")
        }
      }
    }
  }

  test("CTA warning is shown when not all messages are checked and hidden when all messages are checked") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        checkBoxAtIndex(0)
      }

      awaitScreenWithBody<FormBodyModel> {
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList.size.shouldBe(2)
        mainContentList[1].apply {
          shouldBeInstanceOf<FormMainContentModel.Callout>()
          item.shouldNotBeNull().apply {
            title.shouldBe("To wipe your device, please confirm and acknowledge the messages above.")
            subtitle.shouldBeNull()
            treatment.shouldBe(CalloutModel.Treatment.Warning)
          }
        }

        checkBoxAtIndex(1)
      }

      awaitItem()

      awaitScreenWithBody<FormBodyModel> {
        mainContentList.size.shouldBe(1)
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          listGroupModel.items[0].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
            iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.apply {
              shouldBe(Icon.SmallIconCheckboxSelected)
            }
          }
          listGroupModel.items[1].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().model.apply {
            iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.apply {
              shouldBe(Icon.SmallIconCheckboxSelected)
            }
          }
        }
      }
    }
  }

  test("show and dismiss ScanAndWipeConfirmationSheet") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        checkBoxAtIndex(0)
      }

      awaitScreenWithBody<FormBodyModel> {
        checkBoxAtIndex(1)
      }

      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          // Simulate clicking the wipe button to show the sheet
          onClick.invoke()
        }
      }

      awaitItem().bottomSheetModel.shouldNotBeNull().apply {
        body.shouldHaveId(WipingDeviceEventTrackerScreenId.SCAN_AND_RESET_SHEET)
        body.shouldBeInstanceOf<FormBodyModel>().apply {
          secondaryButton.shouldNotBeNull().apply {
            // Simulate clicking the confirm button to dismiss the sheet
            onClick.invoke()
          }
        }
      }

      // Verify the bottom sheet is dismissed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }
})

fun FormBodyModel.checkBoxAtIndex(index: Int) {
  mainContentList[0].apply {
    shouldBeInstanceOf<FormMainContentModel.ListGroup>()

    listGroupModel.items[index].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.IconAccessory>().apply {
      onClick.shouldNotBeNull().invoke()
    }
  }
}
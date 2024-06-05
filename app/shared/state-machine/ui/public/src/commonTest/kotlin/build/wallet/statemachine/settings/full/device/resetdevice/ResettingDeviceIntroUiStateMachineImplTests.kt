package build.wallet.statemachine.settings.full.device.resetdevice

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiStateMachineImpl
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResettingDeviceIntroUiStateMachineImplTests : FunSpec({
  val stateMachine = ResettingDeviceIntroUiStateMachineImpl(
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "resetting device nfc"
      ) {},
    signatureVerifier = SignatureVerifierMock(),
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    currencyConverter = CurrencyConverterFake(conversionRate = 3.0)
  )

  val onBackCalls = turbines.create<Unit>("on back calls")

  val activeKeyboxLoadedData = ActiveKeyboxLoadedDataMock

  val props = ResettingDeviceIntroProps(
    onBack = { onBackCalls += Unit },
    onUnwindToMoneyHome = {},
    onDeviceConfirmed = {},
    spendingWallet = activeKeyboxLoadedData.spendingWallet,
    keybox = activeKeyboxLoadedData.account.keybox,
    balance = activeKeyboxLoadedData.transactionsData.balance,
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

  test("tap to confirm sheet can be shown and dismissed") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          listGroupModel.header.shouldBe("These items will be removed.")
          listGroupModel.items[0].title.shouldBe("Device key")
          listGroupModel.items[1].title.shouldBe("Saved fingerprints")
        }

        primaryButton.shouldNotBeNull()

        primaryButton.shouldBeInstanceOf<ButtonModel>().apply {
          text.shouldBe("Reset device")
          onClick.invoke()
        }
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .secondaryButton?.onClick?.invoke()
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("tap to confirm sheet can be shown and confirmed") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        primaryButton.shouldBeInstanceOf<ButtonModel>().apply {
          text.shouldBe("Reset device")
          onClick.invoke()
        }
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .primaryButton?.onClick?.invoke()
      }

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      // TODO: assert specific values on these sheets
      // Transfer funds warning sheet
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .id.shouldBe(ResettingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS)
      }

      // Transfer funds balance loaded
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .id.shouldBe(ResettingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS)
      }
    }
  }
})

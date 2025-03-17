package build.wallet.statemachine.settings.full.device.wipedevice

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WipingDeviceIntroUiStateMachineImplTests : FunSpec({

  val mobilePayService = MobilePayServiceMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()

  val stateMachine = WipingDeviceIntroUiStateMachineImpl(
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "wiping device nfc"
      ) {},
    signatureVerifier = SignatureVerifierMock(),
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    currencyConverter = CurrencyConverterFake(conversionRate = 3.0),
    mobilePayService = mobilePayService,
    bitcoinWalletService = bitcoinWalletService
  )

  val onBackCalls = turbines.create<Unit>("on back calls")

  val spendingWallet = SpendingWalletMock(turbines::create)

  val props = WipingDeviceIntroProps(
    onBack = { onBackCalls += Unit },
    onUnwindToMoneyHome = {},
    onDeviceConfirmed = {},
    fullAccount = FullAccountMock
  )

  beforeTest {
    bitcoinWalletService.reset()

    bitcoinWalletService.spendingWallet.value = spendingWallet
    bitcoinWalletService.transactionsData.value = TransactionsDataMock
  }

  test("onBack calls") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
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
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()

        primaryButton.shouldBeInstanceOf<ButtonModel>().apply {
          text.shouldBe("Wipe device")
          onClick.invoke()
        }
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .secondaryButton?.onClick?.invoke()
      }

      awaitBody<FormBodyModel>()
    }
  }

  test("tap to confirm sheet can be shown and confirmed") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        primaryButton.shouldBeInstanceOf<ButtonModel>().apply {
          text.shouldBe("Wipe device")
          onClick.invoke()
        }
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .primaryButton?.onClick?.invoke()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<Pair<Secp256k1PublicKey, String>>> {
        onSuccess(Pair(Secp256k1PublicKey("public"), "success"))
      }

      // Transfer funds warning sheet
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
          .id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS)
      }

      // Transfer funds balance loaded
      with(awaitItem()) {
        val bottomSheet = bottomSheetModel.shouldNotBeNull()
        val body = bottomSheet.body.shouldBeInstanceOf<FormBodyModel>()
        body.id.shouldBe(WipingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS)

        val header = body.header.shouldNotBeNull()
        header.headline.shouldBe("Transfer funds before you wipe the device")

        val mainContentList = body.mainContentList.shouldNotBeNull()
        val listGroup = mainContentList[0].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        val listGroupModel = listGroup.listGroupModel.shouldNotBeNull()

        listGroupModel.header.shouldBe("Your funds")
        listGroupModel.items[0].title.shouldBe("$0.00")
        listGroupModel.items[0].secondaryText.shouldBe("100,000 sats")
      }
    }
  }
})

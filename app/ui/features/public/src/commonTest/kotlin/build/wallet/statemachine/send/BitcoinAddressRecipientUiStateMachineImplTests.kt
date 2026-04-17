package build.wallet.statemachine.send

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.signetAddressP2SH
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.*
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty

class BitcoinAddressRecipientUiStateMachineImplTests : FunSpec({
  val validAddress: BitcoinAddress = someBitcoinAddress
  val validInvoice = validBitcoinInvoice
  val validInvoiceUrl = "bitcoin:${validInvoice.address.address}"
  val invalidAddressText: String = someBitcoinAddress.address.dropLast(1)
  // An address defined in [SpendingWalletFake]
  val selfAddress = BitcoinAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq")

  val validSignetAddress = signetAddressP2SH.address
  val validSignetBIP21URI = "bitcoin:$validSignetAddress"
  val lightningInvoiceString = "lnbc_test_invoice"

  val paymentParser =
    PaymentDataParserMock(
      validBip21URIs = mutableSetOf(validInvoiceUrl),
      validAddresses = mutableSetOf(validAddress.address, selfAddress.address),
      validBOLT11Invoices = mutableSetOf(lightningInvoiceString)
    )
  val accountConfigService = AccountConfigServiceFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val clipboardMock = ClipboardMock()
  val appSessionManager = AppSessionManagerFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(featureFlagDao)

  val stateMachine = BitcoinAddressRecipientUiStateMachineImpl(
    paymentDataParser = paymentParser,
    accountConfigService = accountConfigService,
    bitcoinWalletService = bitcoinWalletService,
    clipboard = clipboardMock,
    appSessionManager = appSessionManager,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag
  )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onRecipientEnteredCalls = turbines.create<BitcoinAddress>("on recipient entered")
  val onScanQrCodeClickCalls = turbines.create<Unit>("on scan qrcode calls")
  val onGoToUtxoConsolidationCalls = turbines.create<Unit>("on go to utxo consolidation calls")

  val props =
    BitcoinAddressRecipientUiProps(
      address = null,
      validInvoiceInClipboard = null,
      onBack = {
        onBackCalls += Unit
      },
      onRecipientEntered = {
        onRecipientEnteredCalls += it
      },
      onScanQrCodeClick = {
        onScanQrCodeClickCalls += Unit
      },
      onGoToUtxoConsolidation = {
        onGoToUtxoConsolidationCalls += Unit
      }
    )

  beforeTest {
    accountConfigService.reset()
    accountConfigService.setBitcoinNetworkType(BITCOIN)
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = SpendingWalletFake()
    clipboardMock.plainTextItemToReturn = null
    featureFlagDao.reset()
  }

  test("initial state without default address") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBeEmpty()
        onContinueClick.shouldBeNull()
      }
    }
  }

  test("initial state with default address") {
    stateMachine.test(props.copy(address = validAddress)) {
      awaitOnContinueNotNull(validAddress.address)
    }
  }

  test("click scan qr code") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onScanQrCodeClick()
      }

      onScanQrCodeClickCalls.awaitItem().shouldBe(Unit)
      onScanQrCodeClickCalls.expectNoEvents()
    }
  }

  test("enter valid address") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validAddress.address)
      }

      awaitOnContinueNotNull(validAddress.address)
    }
  }

  test("enter valid invoice url") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validInvoiceUrl)
      }

      awaitOnContinueNotNull(validInvoiceUrl)
    }
  }

  test("enter valid address and continue") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validAddress.address)
      }
      awaitOnContinueNotNull(validAddress.address, click = true)

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid invoice and continue") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validInvoice.address.address)
      }

      awaitOnContinueNotNull(validInvoice.address.address, click = true)

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid address and remove character to make entry invalid") {
    stateMachine.test(props) {
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick == null }
      ) {
        onEnteredTextChanged(validAddress.address)
      }

      val invalidAddress = validAddress.address.dropLast(1)
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick != null }
      ) {
        onEnteredTextChanged(invalidAddress)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick == null }
      ) {
        enteredText.shouldBe(invalidAddress)
      }

      // Ignore duplicate models
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("fix invalid address") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(invalidAddressText)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.enteredText == invalidAddressText }
      ) {
        onEnteredTextChanged(validAddress.address)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.enteredText == validAddress.address && it.onContinueClick != null }
      )
    }
  }

  test("cannot continue when invalid address is entered") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(invalidAddressText)
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(invalidAddressText)
        onContinueClick.shouldBeNull()
      }
      onRecipientEnteredCalls.expectNoEvents()
    }
  }

  test("cannot continue when address from a different bitcoin network is entered") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validSignetAddress)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.enteredText == validSignetAddress && it.onContinueClick == null }
      )

      onRecipientEnteredCalls.expectNoEvents()

      // Ignore duplicate models
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("cannot continue when bip21 uri from a different bitcoin network is entered") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validSignetBIP21URI)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.enteredText == validSignetBIP21URI && it.onContinueClick == null }
      )
      onRecipientEnteredCalls.expectNoEvents()

      // Ignore duplicate models
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("cannot continue when self address is entered") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(selfAddress.address)
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick == null && it.showSelfSendWarningWithRedirect }
      ) {
        onGoToUtxoConsolidation()
      }
      onGoToUtxoConsolidationCalls.awaitItem()
    }
  }

  test("paste button fills text field with fresh clipboard content") {
    clipboardMock.plainTextItemToReturn = PlainText(data = someBitcoinAddress.address)
    stateMachine.test(props) {
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.showPasteButton }
      ) {
        onPasteButtonClick()
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick != null }
      ) {
        enteredText.shouldBe(someBitcoinAddress.address)
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste reads current clipboard, not stale snapshot from flow entry") {
    // Enter flow with selfAddress on clipboard
    clipboardMock.plainTextItemToReturn = PlainText(data = selfAddress.address)
    stateMachine.test(props) {
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.showPasteButton }
      ) {
        // Clipboard changes before user taps paste (simulates app-switch + copy)
        clipboardMock.plainTextItemToReturn = PlainText(data = validAddress.address)
        onPasteButtonClick()
      }

      // Should paste the NEW address (validAddress), not the stale one (selfAddress)
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.onContinueClick != null }
      ) {
        enteredText.shouldBe(validAddress.address)
      }
    }
  }

  test("paste button does not show with contents in address field") {
    clipboardMock.plainTextItemToReturn = PlainText(data = validAddress.address)
    stateMachine.test(props) {
      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { it.showPasteButton }
      ) {
        // Now, user manually enters some text
        onEnteredTextChanged("t")
      }

      awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
        matching = { !it.showPasteButton && it.enteredText == "t" && it.onContinueClick == null }
      )

      // Ignore duplicate models
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("paste button does not show with invalid address in clipboard") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button does not show with Lightning address in clipboard") {
    clipboardMock.plainTextItemToReturn = PlainText(data = lightningInvoiceString)
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button shows with valid address in clipboard") {
    clipboardMock.plainTextItemToReturn = PlainText(data = validAddress.address)
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
      }
    }
  }

  test("paste button shows with valid BIP21 URI in clipboard") {
    clipboardMock.plainTextItemToReturn = PlainText(data = validInvoiceUrl)
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
      }
    }
  }
})

private suspend fun ReceiveTurbine<BodyModel>.awaitOnContinueNotNull(
  address: String,
  click: Boolean = false,
) {
  awaitUntilBodyModel<BitcoinRecipientAddressScreenModel>(
    matching = { it.onContinueClick != null }
  ) {
    enteredText.shouldBe(address)
    onContinueClick.shouldNotBeNull()
      .also {
        if (click) {
          onContinueClick.invoke()
        }
      }
  }
}

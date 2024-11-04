package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.signetAddressP2SH
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.*
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UtxoConsolidationFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.wallet.KeysetWalletProviderMock
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
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

  val paymentParser =
    PaymentDataParserMock(
      validBip21URIs = mutableSetOf(validInvoiceUrl),
      validAddresses = mutableSetOf(validAddress.address, selfAddress.address),
      validBOLT11Invoices = mutableSetOf()
    )

  val utxoConsolidationFeatureFlag = UtxoConsolidationFeatureFlag(FeatureFlagDaoFake())

  val stateMachine =
    BitcoinAddressRecipientUiStateMachineImpl(
      paymentDataParser = paymentParser,
      keysetWalletProvider = KeysetWalletProviderMock(),
      utxoConsolidationFeatureFlag = utxoConsolidationFeatureFlag
    )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onRecipientEnteredCalls = turbines.create<BitcoinAddress>("on recipient entered")
  val onScanQrCodeClickCalls = turbines.create<Unit>("on scan qrcode calls")
  val onGoToUtxoConsolidationCalls = turbines.create<Unit>("on go to utxo consolidation calls")

  val props =
    BitcoinAddressRecipientUiProps(
      address = null,
      // Since our fixtures use mainnet addresses.
      spendingKeyset = SpendingKeysetMock.copy(networkType = BITCOIN),
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
      networkType = BITCOIN,
      onGoToUtxoConsolidation = {
        onGoToUtxoConsolidationCalls += Unit
      }
    )

  beforeTest {
    utxoConsolidationFeatureFlag.reset()
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
      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(validAddress.address)
        onContinueClick.shouldNotBeNull()
      }
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

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(validAddress.address)
        onContinueClick.shouldNotBeNull()
      }
    }
  }

  test("enter valid invoice url") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validInvoiceUrl)
      }

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(validInvoiceUrl)
        onContinueClick.shouldNotBeNull()
      }
    }
  }

  test("enter valid address and continue") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validAddress.address)
      }

      awaitBody<BitcoinRecipientAddressScreenModel> {
        onContinueClick.shouldNotBeNull().invoke()
      }

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid invoice and continue") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validInvoice.address.address)
      }

      awaitBody<BitcoinRecipientAddressScreenModel> {
        onContinueClick.shouldNotBeNull().invoke()
      }

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid address and remove character to make entry invalid") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validAddress.address)
      }

      val invalidAddress = validAddress.address.dropLast(1)
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(invalidAddress)
      }
      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(invalidAddress)
        onContinueClick.shouldBeNull()
      }
    }
  }

  test("fix invalid address") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(invalidAddressText)
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(invalidAddressText)
        onEnteredTextChanged(validAddress.address)
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(validAddress.address)
        onContinueClick.shouldNotBeNull()
      }
    }
  }

  test("cannot continue when invalid address is entered") {
    stateMachine.test(props) {
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

        awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

        awaitBody<BitcoinRecipientAddressScreenModel> {
          onContinueClick.shouldBeNull()
        }
        onRecipientEnteredCalls.expectNoEvents()
      }
    }
  }

  test("cannot continue when bip21 uri from a different bitcoin network is entered") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(validSignetBIP21URI)

        awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

        awaitBody<BitcoinRecipientAddressScreenModel> {
          onContinueClick.shouldBeNull()
        }
        onRecipientEnteredCalls.expectNoEvents()
      }
    }
  }

  test("cannot continue when self address is entered") {
    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(selfAddress.address)
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        warningText.shouldBe("You can’t send to your own address")
        onContinueClick.shouldBeNull()
      }
    }
  }

  test("cannot continue when self address is entered - utxo consolidation enabled") {
    utxoConsolidationFeatureFlag.setFlagValue(true)

    stateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        onEnteredTextChanged(selfAddress.address)
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        onContinueClick.shouldBeNull()

        showSelfSendWarningWithRedirect.shouldBeTrue()
        onGoToUtxoConsolidation()
        onGoToUtxoConsolidationCalls.awaitItem()
      }
    }
  }

  test("paste button fills text field") {
    stateMachine.test(props.copy(validInvoiceInClipboard = Onchain(someBitcoinAddress))) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
        onPasteButtonClick()
      }

      awaitBody<BitcoinRecipientAddressScreenModel> {
        enteredText.shouldBe(someBitcoinAddress.address)
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button does not show with contents in address field") {
    stateMachine.test(props.copy(validInvoiceInClipboard = Onchain(validAddress))) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
        // Now, user manually enters some text
        onEnteredTextChanged("t")
      }

      awaitBody<BitcoinRecipientAddressScreenModel>() // intermittent model

      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button does not show with invalid address in clipboard") {
    val invalidAddressInClipboardStateMachine =
      BitcoinAddressRecipientUiStateMachineImpl(
        paymentDataParser = paymentParser,
        keysetWalletProvider = KeysetWalletProviderMock(),
        utxoConsolidationFeatureFlag = utxoConsolidationFeatureFlag
      )
    invalidAddressInClipboardStateMachine.test(props) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button does not show with Lightning address in clipboard") {
    stateMachine.test(props.copy(validInvoiceInClipboard = validLightningInvoice)) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeFalse()
      }
    }
  }

  test("paste button shows with valid address in clipboard") {
    val validAddressProps =
      props.copy(
        validInvoiceInClipboard = Onchain(validAddress)
      )

    stateMachine.test(validAddressProps) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
      }
    }
  }

  test("paste button shows with valid BIP21 URI in clipboard") {
    // Valid BIP21 URI in clipboard
    val validBIP21URIProps =
      props.copy(
        validInvoiceInClipboard =
          BIP21(
            BIP21PaymentData(
              onchainInvoice = BitcoinInvoice(validAddress),
              lightningInvoice = null
            )
          )
      )

    stateMachine.test(validBIP21URIProps) {
      awaitBody<BitcoinRecipientAddressScreenModel> {
        showPasteButton.shouldBeTrue()
      }
    }
  }
})

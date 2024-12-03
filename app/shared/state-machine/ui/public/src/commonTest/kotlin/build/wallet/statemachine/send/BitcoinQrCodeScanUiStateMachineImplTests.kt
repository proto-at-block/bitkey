package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.bitcoinAddressP2PKH
import build.wallet.bitcoin.address.signetAddressP2SH
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.invoice.PaymentDataParserMock
import build.wallet.bitcoin.invoice.validLightningInvoice
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UtxoConsolidationFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.LabelModel.LinkSubstringModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class BitcoinQrCodeScanUiStateMachineImplTests : FunSpec({
  val validAddress: BitcoinAddress = someBitcoinAddress
  val invalidAddressText: String = someBitcoinAddress.address.dropLast(1)

  // Misaligned network address
  val validSignetAddress = signetAddressP2SH.address
  val validSignetBIP21URI = "bitcoin:$validSignetAddress"
  val selfSendAddress = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"

  val paymentParserMock =
    PaymentDataParserMock(
      validBip21URIs = mutableSetOf(),
      validBip21URIsWithAmount = mutableSetOf(bitcoinAddressP2PKH.address),
      validAddresses = mutableSetOf(someBitcoinAddress.address, selfSendAddress),
      validBOLT11Invoices = mutableSetOf()
    )

  val bitcoinWalletService = BitcoinWalletServiceFake()
  val utxoConsolidationFeatureFlag = UtxoConsolidationFeatureFlag(FeatureFlagDaoFake())

  val stateMachine =
    BitcoinQrCodeScanUiStateMachineImpl(
      paymentDataParser = paymentParserMock,
      bitcoinWalletService = bitcoinWalletService,
      utxoConsolidationFeatureFlag = utxoConsolidationFeatureFlag
    )

  val onEnterAddressClickCalls = turbines.create<Unit>("enter address click calls")
  val onCloseCalls = turbines.create<Unit>("close calls")
  val onRecipientScannedCalls = turbines.create<BitcoinAddress>("recipient scanned calls")
  val onInvoiceScannedCalls = turbines.create<BitcoinInvoice>("invoice scanned calls")
  val onGoToUtxoConsolidationCalls = turbines.create<Unit>("on go to utxo consolidation calls")

  val props =
    BitcoinQrCodeScanUiProps(
      validInvoiceInClipboard = Onchain(validAddress),
      onEnterAddressClick = {
        onEnterAddressClickCalls += Unit
      },
      onClose = {
        onCloseCalls += Unit
      },
      onRecipientScanned = { address ->
        onRecipientScannedCalls += address
      },
      onInvoiceScanned = { invoice ->
        onInvoiceScannedCalls += invoice
      },
      networkType = BITCOIN,
      onGoToUtxoConsolidation = {
        onGoToUtxoConsolidationCalls += Unit
      }
    )

  beforeTest {
    utxoConsolidationFeatureFlag.reset()
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = SpendingWalletFake()
  }

  test("Valid Address in QR code should call onRecipientScanned") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(validAddress.address)
      }

      onRecipientScannedCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("Valid Address with Amount in QR code should call onInvoiceScanned") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(bitcoinAddressP2PKH.address)
      }

      onInvoiceScannedCalls.awaitItem().shouldBe(
        BitcoinInvoice(
          address = bitcoinAddressP2PKH,
          amount = BitcoinMoney.btc(200.0)
        )
      )
    }
  }

  test("Invalid Address in QR code should lead to error screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(invalidAddressText)
      }

      // Error
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("Address from different bitcoin network should lead to error screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(validSignetAddress)
      }

      // Error
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("BIP21 URI from different bitcoin network should lead to error screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(validSignetBIP21URI)
      }

      // Error
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("onClose prop is called onClose of the model") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onClose()
      }

      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onEnterAddressClick prop is called onEnterAddressClick of the model") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onEnterAddressClickCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("valid ParsedPaymentData in clipboard should show paste button") {
    stateMachine.test(props) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        secondaryButton.shouldNotBeNull()
      }
    }
  }

  test("invalid ParsedPaymentData in clipboard should not show paste button") {
    stateMachine.test(props.copy(validInvoiceInClipboard = null)) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("Lightning ParsedPaymentData in clipboard should not show paste button") {
    stateMachine.test(props.copy(validInvoiceInClipboard = validLightningInvoice)) {
      awaitScreenWithBody<QrCodeScanBodyModel> {
        secondaryButton.shouldBeNull()
      }
    }
  }

  context("utxo consolidation disabled") {
    test("Copying a self address leads to error screen") {
      // Address from [SpendingWalletFake]
      val selfSendProps =
        props.copy(
          validInvoiceInClipboard = Onchain(BitcoinAddress(selfSendAddress))
        )
      stateMachine.test(selfSendProps) {
        awaitScreenWithBody<QrCodeScanBodyModel> {
          secondaryButton.shouldNotBeNull().onClick()
        }

        // error from self send
        awaitScreenWithBody<FormBodyModel> {
          header?.headline.shouldBe("You can’t send to your own address")
        }
      }
    }

    test("Scanning a self address leads to error screen") {
      stateMachine.test(props) {
        // scanning QR code from SpendingWalletFake
        awaitScreenWithBody<QrCodeScanBodyModel> {
          onQrCodeScanned(selfSendAddress)
        }

        // error from self send
        awaitScreenWithBody<FormBodyModel> {
          header?.headline.shouldBe("You can’t send to your own address")
        }
      }
    }
  }

  context("utxo consolidation enabled") {
    test("Copying a self address leads to error screen") {
      utxoConsolidationFeatureFlag.setFlagValue(true)

      // Address from [SpendingWalletFake]
      val selfSendProps =
        props.copy(
          validInvoiceInClipboard = Onchain(BitcoinAddress(selfSendAddress))
        )
      stateMachine.test(selfSendProps) {
        awaitScreenWithBody<QrCodeScanBodyModel> {
          secondaryButton.shouldNotBeNull().onClick()
        }

        // error from self send
        awaitScreenWithBody<FormBodyModel> {
          toolbar.shouldNotBeNull().leadingAccessory.shouldBeTypeOf<IconAccessory>()
          header.shouldNotBeNull().headline.shouldBe("This is your Bitkey wallet address")

          // Click on the utxo consolidation sublink
          header.shouldNotBeNull().sublineModel.shouldBeTypeOf<LinkSubstringModel>().linkedSubstrings[0].onClick()
          onGoToUtxoConsolidationCalls.awaitItem()
        }
      }
    }

    test("Scanning a self address leads to error screen") {
      utxoConsolidationFeatureFlag.setFlagValue(true)

      stateMachine.test(props) {
        // scanning QR code from SpendingWalletFake
        awaitScreenWithBody<QrCodeScanBodyModel> {
          onQrCodeScanned(selfSendAddress)
        }

        // error from self send
        awaitScreenWithBody<FormBodyModel> {
          toolbar.shouldNotBeNull().leadingAccessory.shouldBeTypeOf<IconAccessory>()
          header.shouldNotBeNull().headline.shouldBe("This is your Bitkey wallet address")

          // Click on the utxo consolidation sublink
          header.shouldNotBeNull().sublineModel.shouldBeTypeOf<LinkSubstringModel>().linkedSubstrings[0].onClick()
          onGoToUtxoConsolidationCalls.awaitItem()
        }
      }
    }
  }
})

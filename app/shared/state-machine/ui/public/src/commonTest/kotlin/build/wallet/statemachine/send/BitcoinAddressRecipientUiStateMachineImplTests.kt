package build.wallet.statemachine.send

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.signetAddressP2SH
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.BIP21PaymentData
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.invoice.PaymentDataParserMock
import build.wallet.bitcoin.invoice.validBitcoinInvoice
import build.wallet.bitcoin.invoice.validLightningInvoice
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.wallet.KeysetWalletProviderMock
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.AddressInput
import build.wallet.statemachine.core.input.onValueChange
import build.wallet.statemachine.core.test
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

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

  val stateMachine =
    BitcoinAddressRecipientUiStateMachineImpl(
      paymentDataParser = paymentParser,
      keysetWalletProvider = KeysetWalletProviderMock()
    )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onRecipientEnteredCalls = turbines.create<BitcoinAddress>("on recipient entered")
  val onScanQrCodeClickCalls = turbines.create<Unit>("on scan qrcode calls")

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
      networkType = BITCOIN
    )

  test("initial state without default address") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBeEmpty()
        }

        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
      }
    }
  }

  test("initial state with default address") {
    stateMachine.test(props.copy(address = validAddress)) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(validAddress.address)
        }

        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("click scan qr code") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(toolbar?.trailingAccessory.shouldNotBeNull()) {
          shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          model.onClick.shouldNotBeNull().invoke()
        }
      }

      onScanQrCodeClickCalls.awaitItem().shouldBe(Unit)
      onScanQrCodeClickCalls.expectNoEvents()
    }
  }

  test("enter valid address") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validAddress.address)
        }
      }

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(validAddress.address)
        }

        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("enter valid invoice url") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validInvoiceUrl)
        }
      }

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(validInvoiceUrl)
        }

        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("enter valid address and continue") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validAddress.address)
        }
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid invoice and continue") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validInvoiceUrl)
        }
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onRecipientEnteredCalls.awaitItem().shouldBe(validAddress)
    }
  }

  test("enter valid address and remove character to make entry invalid") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validInvoiceUrl)
        }
      }

      val invalidAddress = validAddress.address.dropLast(1)
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(invalidAddress)
        }
      }
      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(invalidAddress)
        }
        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
      }
    }
  }

  test("fix invalid address") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(invalidAddressText)
        }
      }

      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(validAddress.address)
        }
      }

      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(validAddress.address)
        }
        primaryButton.shouldNotBeNull().isEnabled.shouldBeTrue()
      }
    }
  }

  test("cannot continue when invalid address is entered") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validAddress.address)
        }
      }

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(invalidAddressText)
        }
      }

      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      onRecipientEnteredCalls.expectNoEvents()
    }
  }

  test("cannot continue when address from a different bitcoin network is entered") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validSignetAddress)
        }

        awaitBody<FormBodyModel>() // intermittent model

        awaitBody<FormBodyModel> {
          primaryButton.shouldNotBeNull().onClick()
        }
        onRecipientEnteredCalls.expectNoEvents()
      }
    }
  }

  test("cannot continue when bip21 uri from a different bitcoin network is entered") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(validSignetBIP21URI)
        }

        awaitBody<FormBodyModel>() // intermittent model

        awaitBody<FormBodyModel> {
          primaryButton.shouldNotBeNull().onClick()
        }
        onRecipientEnteredCalls.expectNoEvents()
      }
    }
  }

  test("cannot continue when self address is entered") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.onValueChange(selfAddress.address)
        }
      }

      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.Explainer>()) {
          items.first().body.shouldBeInstanceOf<LabelModel.StringModel>()
            .string.shouldBe("Sorry, you can’t send to your own address")
        }
        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
      }
    }
  }

  test("paste button fills text field") {
    stateMachine.test(props.copy(validInvoiceInClipboard = Onchain(someBitcoinAddress))) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldNotBeNull().onClick()
        }
      }

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          fieldModel.value.shouldBe(someBitcoinAddress.address)
        }
      }
    }
  }

  test("paste button does not show with contents in address field") {
    stateMachine.test(props.copy(validInvoiceInClipboard = Onchain(validAddress))) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldNotBeNull()
          // Now, user manually enters some text
          fieldModel.onValueChange("t")
        }
      }

      awaitBody<FormBodyModel>() // intermittent model

      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldBeNull()
        }
      }
    }
  }

  test("paste button does not show with invalid address in clipboard") {
    val invalidAddressInClipboardStateMachine =
      BitcoinAddressRecipientUiStateMachineImpl(
        paymentDataParser = paymentParser,
        keysetWalletProvider = KeysetWalletProviderMock()
      )
    invalidAddressInClipboardStateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldBeNull()
        }
      }
    }
  }

  test("paste button does not show with Lightning address in clipboard") {
    stateMachine.test(props.copy(validInvoiceInClipboard = validLightningInvoice)) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldBeNull()
        }
      }
    }
  }

  test("paste button shows with valid address in clipboard") {
    val validAddressProps =
      props.copy(
        validInvoiceInClipboard = Onchain(validAddress)
      )

    stateMachine.test(validAddressProps) {
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldNotBeNull()
        }
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
      awaitBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<AddressInput>()) {
          trailingButtonModel.shouldNotBeNull()
        }
      }
    }
  }
})

package build.wallet.statemachine.receive

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.sharing.SharingManagerMock
import build.wallet.platform.sharing.SharingManagerMock.SharedText
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.time.ControlledDelayer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class AddressQrCodeUiStateMachineImplTests : FunSpec({
  val clipboard = ClipboardMock()

  val sharingManager = SharingManagerMock(turbines::create)
  val bitcoinAddressService = BitcoinAddressServiceFake()

  val stateMachine =
    AddressQrCodeUiStateMachineImpl(
      clipboard = clipboard,
      delayer = ControlledDelayer(),
      sharingManager = sharingManager,
      bitcoinInvoiceUrlEncoder = BitcoinInvoiceUrlEncoderMock(),
      bitcoinAddressService = bitcoinAddressService
    )

  val onBackCalls = turbines.create<Unit>("back calls")
  val props =
    AddressQrCodeUiProps(
      account = FullAccountMock,
      onBack = {
        onBackCalls += Unit
      }
    )

  beforeTest {
    bitcoinAddressService.reset()
  }

  test("show screen with address and QR code") {
    stateMachine.test(props) {
      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          addressQrImageUrl.shouldBeNull()
          fallbackAddressQrCodeModel.shouldBeNull()
        }
      }

      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          addressQrImageUrl.shouldBe("https://api.cash.app/qr/btc/${someBitcoinAddress.address}?currency=btc&logoColor=000000&rounded=true&size=2000&errorCorrection=2")
          fallbackAddressQrCodeModel.shouldBe(QrCodeModel("bitcoin:${someBitcoinAddress.address}"))
        }
      }
    }
  }

  test("get new address when onRefreshClick called") {
    stateMachine.test(props) {
      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {}

      // Showing address from spendingKeysetAddressProvider flow
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")

        val newAddress = BitcoinAddress("new1ksdjfksljfdsklj1234")
        bitcoinAddressService.result = Ok(newAddress)

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .addressDisplayString.string.shouldBe("...")
      }

      // Showing address from spendingKeysetAddressProvider getNewAddress
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")
      }
    }
  }

  test("copy address to clipboard") {
    stateMachine.test(props) {
      awaitItem()

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .copyButtonModel.onClick()
      }

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .copyButtonModel.leadingIcon.shouldNotBeNull().shouldBe(Icon.SmallIconCheckFilled)
      }

      clipboard.copiedItems.awaitItem().shouldBe(PlainText(someBitcoinAddress.address))

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .copyButtonModel.leadingIcon.shouldNotBeNull().shouldBe(Icon.SmallIconCopy)
      }
    }
  }

  test("share address") {
    stateMachine.test(props) {
      awaitItem()

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<QrCode>()
          .shareButtonModel.onClick()
      }

      sharingManager.sharedTextCalls.awaitItem().shouldBe(
        SharedText(
          text = someBitcoinAddress.address,
          title = "Bitcoin Address"
        )
      )
    }
  }
})

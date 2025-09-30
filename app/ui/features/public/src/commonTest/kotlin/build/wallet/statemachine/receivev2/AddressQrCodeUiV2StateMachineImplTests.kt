package build.wallet.statemachine.receivev2

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
import build.wallet.statemachine.core.test
import build.wallet.statemachine.qr.QrCodeServiceImpl
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeUiProps
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceServiceFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

class AddressQrCodeUiV2StateMachineImplTests : FunSpec({
  val clipboard = ClipboardMock()

  val sharingManager = SharingManagerMock(turbines::create)
  val bitcoinAddressService = BitcoinAddressServiceFake()
  val themePreferenceService = ThemePreferenceServiceFake()
  val qrCodeService = QrCodeServiceImpl()

  val stateMachine = AddressQrCodeUiV2StateMachineImpl(
    clipboard = clipboard,
    restoreCopyAddressStateDelay = RestoreCopyAddressStateDelay(10.milliseconds),
    sharingManager = sharingManager,
    bitcoinInvoiceUrlEncoder = BitcoinInvoiceUrlEncoderMock(),
    bitcoinAddressService = bitcoinAddressService,
    qrCodeService = qrCodeService
  )

  val onBackCalls = turbines.create<Unit>("back calls")
  val props = AddressQrCodeUiProps(
    account = FullAccountMock,
    onBack = {
      onBackCalls += Unit
    }
  )

  beforeTest {
    bitcoinAddressService.reset()
    themePreferenceService.clearThemePreference()
  }

  test("show screen with address and QR code w/ light mode") {
    themePreferenceService.setThemePreference(ThemePreference.Manual(Theme.LIGHT))
    stateMachine.test(props) {
      // Loading address and QR code
      awaitBody<AddressQrCodeV2BodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      awaitBody<AddressQrCodeV2BodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
          qrCodeState.matrix.columnWidth.shouldBe(37)
          qrCodeState.matrix.data.shouldNotBeEmpty()
          qrCodeState.matrix.data.size.shouldBe(37 * 37)
        }
      }
    }
  }

  test("show screen with address and QR code w/ dark mode") {
    themePreferenceService.setThemePreference(ThemePreference.Manual(Theme.DARK))
    stateMachine.test(props) {
      // Loading address and QR code
      awaitBody<AddressQrCodeV2BodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      awaitBody<AddressQrCodeV2BodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
          qrCodeState.matrix.columnWidth.shouldBe(37)
          qrCodeState.matrix.data.shouldNotBeEmpty()
          qrCodeState.matrix.data.size.shouldBe(37 * 37)
        }
      }
    }
  }

  test("get new address when onRefreshClick called") {
    stateMachine.test(props) {
      // Loading address and QR code
      awaitBody<AddressQrCodeV2BodyModel> {}

      // Showing address from spendingKeysetAddressProvider flow
      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")

        val newAddress = BitcoinAddress("new1ksdjfksljfdsklj1234")
        bitcoinAddressService.result = Ok(newAddress)

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading address and QR code
      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("...")
      }

      // Showing address from spendingKeysetAddressProvider getNewAddress
      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")
      }
    }
  }

  test("copy address to clipboard") {
    stateMachine.test(props) {
      awaitItem()

      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .copyButtonModel.onClick()
      }

      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .copyButtonModel.leadingIcon.shouldNotBeNull().shouldBe(Icon.SmallIconCheckFilled)
      }

      clipboard.copiedItems.awaitItem().shouldBe(PlainText(someBitcoinAddress.address))

      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
          .copyButtonModel.leadingIcon.shouldNotBeNull().shouldBe(Icon.SmallIconCopy)
      }
    }
  }

  test("share address") {
    stateMachine.test(props) {
      awaitItem()

      awaitBody<AddressQrCodeV2BodyModel> {
        content.shouldBeTypeOf<AddressQrCodeV2BodyModel.Content.QrCode>()
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

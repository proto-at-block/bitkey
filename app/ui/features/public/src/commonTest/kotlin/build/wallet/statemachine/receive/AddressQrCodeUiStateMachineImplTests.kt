package build.wallet.statemachine.receive

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClientMock
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClientMock
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.ktor.result.HttpError
import build.wallet.partnerships.*
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.haptics.HapticsMock
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.platform.sharing.SharingManagerMock
import build.wallet.platform.sharing.SharingManagerMock.SharedText
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.qr.QrCodeServiceFake
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceServiceFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

class AddressQrCodeUiStateMachineImplTests : FunSpec({
  val clipboard = ClipboardMock()

  val sharingManager = SharingManagerMock(turbines::create)
  val bitcoinAddressService = BitcoinAddressServiceFake()
  val themePreferenceService = ThemePreferenceServiceFake()
  val qrCodeService = QrCodeServiceFake()
  val getTransferPartnerListF8eClient = GetTransferPartnerListF8eClientMock(turbines::create)
  val getTransferRedirectF8eClient = GetTransferRedirectF8eClientMock(turbines::create)
  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val deepLinkHandler = DeepLinkHandlerMock(turbines::create)
  val haptics = HapticsMock()
  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine = AddressQrCodeUiStateMachineImpl(
    clipboard = clipboard,
    restoreCopyAddressStateDelay = RestoreCopyAddressStateDelay(10.milliseconds),
    sharingManager = sharingManager,
    bitcoinInvoiceUrlEncoder = BitcoinInvoiceUrlEncoderMock(),
    bitcoinAddressService = bitcoinAddressService,
    qrCodeService = qrCodeService,
    getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
    getTransferRedirectF8eClient = getTransferRedirectF8eClient,
    partnershipTransactionsService = partnershipTransactionsService,
    deepLinkHandler = deepLinkHandler,
    haptics = haptics,
    eventTracker = eventTracker
  )

  val onBackCalls = turbines.create<Unit>("back calls")
  val onWebLinkOpenedCalls = turbines.create<Triple<String, PartnerInfo, PartnershipTransaction>>("web link opened calls")

  fun props() =
    AddressQrCodeUiProps(
      account = FullAccountMock,
      onBack = {
        onBackCalls += Unit
      },
      onWebLinkOpened = { url, partnerInfo, transaction ->
        onWebLinkOpenedCalls += Triple(url, partnerInfo, transaction)
      }
    )

  beforeTest {
    bitcoinAddressService.reset()
    qrCodeService.reset()
    themePreferenceService.clearThemePreference()
    getTransferPartnerListF8eClient.reset()
    getTransferRedirectF8eClient.reset()
    partnershipTransactionsService.reset()
    deepLinkHandler.reset()
  }

  test("show screen with address and QR code w/ light mode") {
    themePreferenceService.setThemePreference(ThemePreference.Manual(Theme.LIGHT))
    stateMachine.test(props()) {
      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      // Address loaded, now loading partners
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
        }
      }
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
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
    stateMachine.test(props()) {
      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      // Address loaded, now loading partners
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
        }
      }
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
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
    stateMachine.test(props()) {
      // Loading address and QR code
      awaitBody<AddressQrCodeBodyModel> {}

      // Address loaded, now loading partners
      awaitBody<AddressQrCodeBodyModel> {}
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Showing address with partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")

        val newAddress = BitcoinAddress("new1ksdjfksljfdsklj1234")
        bitcoinAddressService.result = Ok(newAddress)

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading new address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("...")
      }

      // New address loaded (partners already loaded, no need to reload)
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")
      }
    }
  }

  test("copy address to clipboard") {
    stateMachine.test(props()) {
      // Loading address
      awaitItem()

      // Address loaded, loading partners
      awaitItem()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded, now test copy
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .onCopyClick()
      }

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .copyButtonIcon.shouldBe(Icon.SmallIconCheckFilled)
      }

      clipboard.copiedItems.awaitItem().shouldBe(PlainText(someBitcoinAddress.address))

      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .copyButtonIcon.shouldBe(Icon.SmallIconCopy)
      }
    }
  }

  test("share address") {
    stateMachine.test(props()) {
      // Loading address
      awaitItem()

      // Address loaded, loading partners
      awaitItem()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded, now test share
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .onShareClick()
      }

      sharingManager.sharedTextCalls.awaitItem().shouldBe(
        SharedText(
          text = someBitcoinAddress.address,
          title = "Bitcoin Address"
        )
      )
    }
  }

  test("load partners and track analytics events") {
    stateMachine.test(props()) {
      // Loading address
      awaitBody<AddressQrCodeBodyModel>()

      // Address loaded, loading partners
      awaitBody<AddressQrCodeBodyModel>()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics tracking for both partners
      val partner1 = PartnerInfo("LogoUrl", "LogoBadgedUrl", "Partner 1", PartnerId("Partner1"))
      val partner2 = PartnerInfo(null, null, "Partner 2", PartnerId("Partner2"))

      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe(partner1.partnerId.value)
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe(partner2.partnerId.value)
        }
      }

      // Partners should be loaded in the model
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          partners.size.shouldBe(2)
          partners[0].name.shouldBe("Partner 1")
          partners[1].name.shouldBe("Partner 2")
        }
      }
    }
  }

  test("redirect to partner when partner clicked") {
    stateMachine.test(props()) {
      // Loading address
      awaitBody<AddressQrCodeBodyModel>()

      // Address loaded, loading partners
      awaitBody<AddressQrCodeBodyModel>()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded, click on partner
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          partners.size.shouldBe(2)
          partners[1].name.shouldBe("Partner 2")

          // Click on Partner 2
          onPartnerClick(partners[1])
        }
      }

      // Allow state transition (LoadingPartnerRedirect state)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for redirect API call (triggered by LaunchedEffect)
      getTransferRedirectF8eClient.getTransferPartnersRedirectCall.awaitItem()

      // Verify transaction was created
      val partner2 = PartnerInfo(null, null, "Partner 2", PartnerId("Partner2"))
      partnershipTransactionsService.createCalls.awaitItem().should { (partnerInfo, type) ->
        type.shouldBe(PartnershipTransactionType.TRANSFER)
        partnerInfo.shouldBe(partner2)
      }

      // Verify redirect callback was invoked with WIDGET type
      onWebLinkOpenedCalls.awaitItem().should { (url, partnerInfo, _) ->
        url.shouldBe("http://example.com/redirect_url")
        partnerInfo.shouldBe(partner2)
      }

      // Should return to loaded state after redirect
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
      }
    }
  }

  test("handle deeplink redirect type") {
    // Configure redirect to use DEEPLINK type instead of WIDGET
    getTransferRedirectF8eClient.transferRedirectResult = Ok(
      build.wallet.f8e.partnerships.GetTransferRedirectF8eClient.Success(
        build.wallet.f8e.partnerships.RedirectInfo(
          appRestrictions = null,
          url = "cashapp://transfer",
          redirectType = RedirectUrlType.DEEPLINK,
          partnerTransactionId = PartnershipTransactionId("some-partner-transaction-id")
        )
      )
    )

    stateMachine.test(props()) {
      // Loading address
      awaitBody<AddressQrCodeBodyModel>()

      // Address loaded, loading partners
      awaitBody<AddressQrCodeBodyModel>()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded, click on partner
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          // Click on Partner 1
          onPartnerClick(partners[0])
        }
      }

      // Allow state transition (LoadingPartnerRedirect state)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for redirect API call (triggered by LaunchedEffect)
      getTransferRedirectF8eClient.getTransferPartnersRedirectCall.awaitItem()

      // Verify transaction was created
      partnershipTransactionsService.createCalls.awaitItem()

      // Verify deeplink was opened (not web link)
      deepLinkHandler.openDeeplinkCalls.awaitItem().shouldBe("cashapp://transfer")

      // Should return to loaded state after redirect
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
      }
    }
  }

  test("unable to load partners continues with empty partner list") {
    getTransferPartnerListF8eClient.partnersResult =
      Err(HttpError.NetworkError(Error("Network error")))

    stateMachine.test(props()) {
      // Loading address
      awaitBody<AddressQrCodeBodyModel>()

      // Address loaded, loading partners
      awaitBody<AddressQrCodeBodyModel>()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Partner loading failed, should still show address screen with empty partners
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          partners.size.shouldBe(0)
        }
      }
    }
  }

  test("handle partner redirect error") {
    getTransferRedirectF8eClient.transferRedirectResult =
      Err(HttpError.NetworkError(Error("Network error")))

    stateMachine.test(props()) {
      // Loading address
      awaitBody<AddressQrCodeBodyModel>()

      // Address loaded, loading partners
      awaitBody<AddressQrCodeBodyModel>()
      getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

      // Verify analytics events were tracked when partners loaded
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner1")
        }
      }
      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("Partner2")
        }
      }

      // Partners loaded, click on partner
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          // Click on Partner 1
          onPartnerClick(partners[0])
        }
      }

      // Allow state transition (LoadingPartnerRedirect state)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for redirect API call (triggered by LaunchedEffect)
      getTransferRedirectF8eClient.getTransferPartnersRedirectCall.awaitItem()

      // Should show error state
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.Error>()) {
          title.shouldBe("Couldn't open Partner 1")
          subline.shouldBe("Please try again later.")
        }
      }
    }
  }
})

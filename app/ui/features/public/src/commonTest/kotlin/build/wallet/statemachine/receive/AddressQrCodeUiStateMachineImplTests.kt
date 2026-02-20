package build.wallet.statemachine.receive

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressInfo
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoderMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClientMock
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClientMock
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.partnerships.*
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.haptics.HapticsMock
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.platform.sharing.SharingManagerMock
import build.wallet.platform.sharing.SharingManagerMock.SharedText
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineFake
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.qr.QrCodeServiceFake
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.root.AddressQrCodeLoadingDuration
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

class AddressQrCodeUiStateMachineImplTests : FunSpec({
  val clipboard = ClipboardMock()

  val sharingManager = SharingManagerMock(turbines::create)
  val bitcoinAddressService = BitcoinAddressServiceFake()
  val bitcoinInvoiceUrlEncoder = BitcoinInvoiceUrlEncoderMock()
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
  val nfcCommands = NfcCommandsMock(turbines::create)
  val nfcSession = NfcSessionFake()
  val nfcSessionUIStateMachine = NfcSessionUIStateMachineFake(
    nfcSession = nfcSession,
    nfcCommands = nfcCommands
  )

  val stateMachine = AddressQrCodeUiStateMachineImpl(
    clipboard = clipboard,
    restoreCopyAddressStateDelay = RestoreCopyAddressStateDelay(10.milliseconds),
    sharingManager = sharingManager,
    bitcoinInvoiceUrlEncoder = bitcoinInvoiceUrlEncoder,
    bitcoinAddressService = bitcoinAddressService,
    qrCodeService = qrCodeService,
    getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
    getTransferRedirectF8eClient = getTransferRedirectF8eClient,
    partnershipTransactionsService = partnershipTransactionsService,
    deepLinkHandler = deepLinkHandler,
    haptics = haptics,
    eventTracker = eventTracker,
    addressQrCodeLoadingDuration = AddressQrCodeLoadingDuration(0.milliseconds),
    nfcSessionUIStateMachine = nfcSessionUIStateMachine
  )

  val onBackCalls = turbines.create<Unit>("back calls")
  val onWebLinkOpenedCalls = turbines.create<Triple<String, PartnerInfo, PartnershipTransaction>>("web link opened calls")

  fun props(account: build.wallet.bitkey.account.FullAccount = FullAccountMock) =
    AddressQrCodeUiProps(
      account = account,
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
    nfcCommands.reset()
  }

  test("show screen with address and QR code w/ light mode") {
    themePreferenceService.setThemePreference(ThemePreference.Manual(Theme.LIGHT))
    stateMachine.test(props()) {
      // Loading address and QR code (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
        }
      }

      // Address loaded - partners may still be loading
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

      // Partners loaded - state updates with partners list
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
          qrCodeState.matrix.columnWidth.shouldBe(37)
          qrCodeState.matrix.data.shouldNotBeEmpty()
          qrCodeState.matrix.data.size.shouldBe(37 * 37)
          partners.size.shouldBe(2)
        }
      }
    }
  }

  test("show screen with address and QR code w/ dark mode") {
    themePreferenceService.setThemePreference(ThemePreference.Manual(Theme.DARK))
    stateMachine.test(props()) {
      // Loading address and QR code (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("...")
          qrCodeState.shouldBe(QrCodeState.Loading)
        }
      }

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
        }
      }

      // Address loaded - partners may still be loading
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

      // Partners loaded - state updates with partners list
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
          qrCodeState.shouldBeInstanceOf<QrCodeState.Success>()
          qrCodeState.matrix.columnWidth.shouldBe(37)
          qrCodeState.matrix.data.shouldNotBeEmpty()
          qrCodeState.matrix.data.size.shouldBe(37 * 37)
          partners.size.shouldBe(2)
        }
      }
    }
  }

  test("get new address when onRefreshClick called") {
    stateMachine.test(props()) {
      // Loading address and QR code (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel> {}

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel> {}

      // Wait for partner loading to start
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
        bitcoinAddressService.addressInfoResult = Ok(BitcoinAddressInfo(address = newAddress, index = 1u))

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading new address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
      }

      // QR code ready for new address
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")
      }

      // New address loaded (partners already loaded, no need to reload)
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")

        val newerAddress = BitcoinAddress("new2ksdjfksljfdsklj5678")
        bitcoinAddressService.addressInfoResult = Ok(BitcoinAddressInfo(address = newerAddress, index = 2u))

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading newer address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new1 ksdj fksl jfds klj1 234")
      }

      // QR code ready for newer address
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new2 ksdj fksl jfds klj5 678")
      }

      // Newer address loaded
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new2 ksdj fksl jfds klj5 678")

        val newestAddress = BitcoinAddress("new3ksdjfksljfdsklj9012")
        bitcoinAddressService.addressInfoResult = Ok(BitcoinAddressInfo(address = newestAddress, index = 3u))

        toolbarModel.trailingAccessory
          .shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick()
      }

      // Loading newest address and QR code
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new2 ksdj fksl jfds klj5 678")
      }

      // QR code ready for newest address
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new3 ksdj fksl jfds klj9 012")
      }

      // Newest address loaded
      awaitBody<AddressQrCodeBodyModel> {
        content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()
          .addressDisplayString.string.shouldBe("new3 ksdj fksl jfds klj9 012")
      }
    }
  }

  test("copy address to clipboard") {
    stateMachine.test(props()) {
      // Loading address (partners load in parallel)
      awaitItem()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitItem()

      // Wait for partner loading to start
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
      // Loading address (partners load in parallel)
      awaitItem()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitItem()

      // Wait for partner loading to start
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
      // Loading address (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel>()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for partner loading to start
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
      // Loading address (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel>()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for partner loading to start
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
      // Loading address (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel>()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for partner loading to start
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
      // Loading address (partners load in parallel but will fail)
      awaitBody<AddressQrCodeBodyModel>()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for partner loading to start (will fail)
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
      // Loading address (partners load in parallel)
      awaitBody<AddressQrCodeBodyModel>()

      // QR code ready (still in loading state, showing QR but animating text)
      awaitBody<AddressQrCodeBodyModel>()

      // Wait for partner loading to start
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

  test("W1 account does not show verify on device button") {
    stateMachine.test(props(account = FullAccountMock)) {
      awaitPartnersLoaded(getTransferPartnerListF8eClient, eventTracker)

      // Partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          showVerifyOnDeviceButton.shouldBe(false)
          onVerifyOnDeviceClick.shouldBe(null)
        }
      }
    }
  }

  test("W3 account shows verify on device button") {
    stateMachine.test(props(account = FullAccountW3Mock)) {
      awaitPartnersLoaded(getTransferPartnerListF8eClient, eventTracker)

      // Partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          showVerifyOnDeviceButton.shouldBe(true)
          onVerifyOnDeviceClick.shouldNotBeNull()
        }
      }
    }
  }

  test("W3 verify on device button triggers NFC session") {
    stateMachine.test(props(account = FullAccountW3Mock)) {
      awaitPartnersLoaded(getTransferPartnerListF8eClient, eventTracker)

      // Partners loaded, click verify on device button
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          showVerifyOnDeviceButton.shouldBe(true)
          onVerifyOnDeviceClick.shouldNotBeNull()
          onVerifyOnDeviceClick!!.invoke()
        }
      }

      // NFC session is shown (fake returns BodyModelMock)
      awaitBody<BodyModelMock<*>> {
        id.shouldBe("nfc-session")
      }

      // After NFC session completes (onSuccess called), return to address loaded state
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
        }
      }
    }
  }

  test("QR code encodes Bitcoin invoice URI, not plain address") {
    stateMachine.test(props()) {
      awaitPartnersLoaded(getTransferPartnerListF8eClient, eventTracker)

      // Partners loaded
      awaitBody<AddressQrCodeBodyModel> {
        with(content.shouldBeTypeOf<AddressQrCodeBodyModel.Content.QrCode>()) {
          addressDisplayString.string.shouldBe("bc1z w508 d6qe jxtd g4y5 r3za rvar yvax xpcs")
        }
      }

      val qrCodeData = qrCodeService.lastGeneratedQrCodeData
      qrCodeData.shouldNotBeNull()

      qrCodeData.shouldBe("bitcoin:${someBitcoinAddress.address}")

      qrCodeData.shouldNotBe(someBitcoinAddress.address)
    }
  }
})

/**
 * Helper to await partner loading sequence without assertions on the events.
 * Use this when you don't need to verify the specific analytics event contents.
 */
private suspend fun StateMachineTester<AddressQrCodeUiProps, BodyModel>.awaitPartnersLoaded(
  getTransferPartnerListF8eClient: GetTransferPartnerListF8eClientMock,
  eventTracker: EventTrackerMock,
) {
  // Loading address (partners load in parallel)
  awaitBody<AddressQrCodeBodyModel>()

  // QR code ready (still in loading state, showing QR but animating text)
  awaitBody<AddressQrCodeBodyModel>()

  // Wait for partner loading to start
  getTransferPartnerListF8eClient.getTransferPartnersCall.awaitItem()

  // Consume analytics events tracked when partners loaded
  eventTracker.eventCalls.awaitItem()
  eventTracker.eventCalls.awaitItem()
}

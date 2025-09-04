package build.wallet.statemachine.partnerships.transferlink

import build.wallet.analytics.events.screen.id.PartnershipsEventTrackerScreenId.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkUiStateMachineImpl.TransferLinkLoadingPartnersBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PartnerTransferLinkUiStateMachineImplTests : FunSpec({
  val partnershipTransferLinkService = PartnershipTransferLinkServiceFake()
  val deepLinkHandler = DeepLinkHandlerMock(turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = PartnerTransferLinkUiStateMachineImpl(
    partnershipTransferLinkService = partnershipTransferLinkService,
    deepLinkHandler = deepLinkHandler,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val testRequest = PartnerTransferLinkRequest(
    partner = "Test Partner",
    event = "deposit",
    eventId = "test-event-123"
  )

  val testHostScreen = ScreenModel(
    body = LoadingBodyModel(id = null),
    presentationStyle = ScreenPresentationStyle.Root
  )

  val onCompleteCalls = turbines.create<Unit>("onCompleteCalls")
  val onExitCalls = turbines.create<Unit>("onExitCalls")
  val props = PartnerTransferLinkProps(
    request = testRequest,
    hostScreen = testHostScreen,
    onComplete = { onCompleteCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    partnershipTransferLinkService.reset()
    deepLinkHandler.reset()
    inAppBrowserNavigator.reset()
  }

  test("shows loading then confirmation sheet when partner info loads successfully") {
    stateMachine.test(props) {
      // First shows loading partner info
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)

      // Then shows confirmation sheet
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        partnerInfo.shouldBe(partnershipTransferLinkService.defaultPartnerInfo)
      }
    }
  }

  test("shows partner not found error when partner lookup fails") {
    partnershipTransferLinkService.getPartnerInfoForPartnerResult =
      Err(GetPartnerInfoError.PartnerNotFound(Error("Partner not found")))

    stateMachine.test(props) {
      // Loading partner info
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)

      // Partner not found error
      awaitUntilBody<FormBodyModel> {
        header!!.headline.shouldBe("We hit a snag...")
        header!!.sublineModel!!.string.shouldBe("Check your connection and try again.")
        primaryButton!!.text.shouldBe("Try again")
        secondaryButton!!.text.shouldBe("Cancel")
      }
    }
  }

  test("completes flow when deeplink opens successfully") {
    val redirectInfo = TransferLinkRedirectInfo(
      redirectMethod = PartnerRedirectionMethod.Deeplink(
        urlString = "partner://transfer?token=abc123",
        appRestrictions = null,
        partnerName = "Test Partner"
      ),
      partnerName = "Test Partner"
    )
    partnershipTransferLinkService.processTransferLinkResult = Ok(redirectInfo)

    stateMachine.test(props) {
      // Loading partner info
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)

      // Confirmation sheet
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Processing
      awaitUntilBody<LoadingSuccessBodyModel>(PARTNER_TRANSFER_LINK_PROCESSING)

      // Verify deeplink was called and flow completed
      deepLinkHandler.openDeeplinkCalls.awaitItem().shouldBe("partner://transfer?token=abc123")
      onCompleteCalls.awaitItem()
    }
  }

  test("shows error when deeplink fails to open") {
    val redirectInfo = TransferLinkRedirectInfo(
      redirectMethod = PartnerRedirectionMethod.Deeplink(
        urlString = "partner://transfer?token=abc123",
        appRestrictions = null,
        partnerName = "Test Partner"
      ),
      partnerName = "Test Partner"
    )
    partnershipTransferLinkService.processTransferLinkResult = Ok(redirectInfo)
    deepLinkHandler.openDeeplinkResult = OpenDeeplinkResult.Failed

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      deepLinkHandler.openDeeplinkCalls.awaitItem()

      // Wait for redirect error state
      awaitUntilBody<FormBodyModel> {
        header!!.headline.shouldBe("We couldn’t redirect you back to Test Partner.")
        primaryButton!!.text.shouldBe("Got it")
      }
    }
  }

  test("shows error when deeplink app restriction check fails") {
    val appRestrictions = AppRestrictions(
      minVersion = 1L,
      packageName = "com.partner.app"
    )
    val redirectInfo = TransferLinkRedirectInfo(
      redirectMethod = PartnerRedirectionMethod.Deeplink(
        urlString = "partner://transfer?token=abc123",
        appRestrictions = appRestrictions,
        partnerName = "Test Partner"
      ),
      partnerName = "Test Partner"
    )
    partnershipTransferLinkService.processTransferLinkResult = Ok(redirectInfo)
    deepLinkHandler.openDeeplinkResult = OpenDeeplinkResult.Opened(
      appRestrictionResult = OpenDeeplinkResult.AppRestrictionResult.Failed(appRestrictions)
    )

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      deepLinkHandler.openDeeplinkCalls.awaitItem()

      // Wait for redirect error state
      awaitUntilBody<FormBodyModel> {
        header!!.headline.shouldBe("We couldn’t redirect you back to Test Partner.")
        primaryButton!!.text.shouldBe("Got it")
      }
    }
  }

  test("shows in-app browser for web redirection") {
    val webUrl = "https://partner.me/partner/bitkey?token=abc123"
    val partnerInfo = PartnerInfo(
      logoUrl = "https://example.com/logo.png",
      logoBadgedUrl = "https://example.com/logo-badge.png",
      name = "Test Partner",
      partnerId = PartnerId("test-partner")
    )
    val redirectInfo = TransferLinkRedirectInfo(
      redirectMethod = PartnerRedirectionMethod.Web(
        urlString = webUrl,
        partnerInfo = partnerInfo
      ),
      partnerName = "Test Partner"
    )
    partnershipTransferLinkService.processTransferLinkResult = Ok(redirectInfo)

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Wait for browser state
      awaitUntilBody<InAppBrowserModel> {
        open.invoke()

        // Verify browser was opened with correct URL
        inAppBrowserNavigator.onOpenCalls.awaitItem().shouldBe(webUrl)
      }
    }
  }

  test("shows retryable error when transfer link processing fails with retryable error") {
    val retryableError = ProcessTransferLinkError.Retryable(Error("Network timeout"))
    partnershipTransferLinkService.processTransferLinkResult = Err(retryableError)

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Get to error state
      awaitUntilBody<FormBodyModel> {
        id!!.shouldBe(PARTNER_TRANSFER_LINK_RETRYABLE_ERROR)
        header!!.headline.shouldBe("We hit a snag...")
        primaryButton!!.text.shouldBe("Try again")
        secondaryButton!!.text.shouldBe("Cancel")
      }
    }
  }

  test("shows non-retryable error when transfer link processing fails with non-retryable error") {
    val nonRetryableError = ProcessTransferLinkError.NotRetryable(Error("oops"))
    partnershipTransferLinkService.processTransferLinkResult = Err(nonRetryableError)

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Get to error state
      awaitUntilBody<FormBodyModel> {
        id!!.shouldBe(PARTNER_TRANSFER_LINK_UNRETRYABLE_ERROR)
        header!!.headline.shouldBe("We couldn’t link your Bitkey to Test Partner")
        primaryButton!!.text.shouldBe("Got it")
        secondaryButton.shouldBe(null) // No retry for non-retryable errors
      }
    }
  }

  test("retries processing when retry button is clicked in retryable error state") {
    val retryableError = ProcessTransferLinkError.Retryable(Error("Network error"))
    partnershipTransferLinkService.processTransferLinkResult = Err(retryableError)

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Get to error state
      awaitUntilBody<FormBodyModel> {
        // Set up success for retry
        val successRedirectInfo = TransferLinkRedirectInfo(
          redirectMethod = PartnerRedirectionMethod.Deeplink(
            urlString = "partner://transfer?token=abc123",
            appRestrictions = null,
            partnerName = "Test Partner"
          ),
          partnerName = "Test Partner"
        )
        partnershipTransferLinkService.processTransferLinkResult = Ok(successRedirectInfo)

        // Trigger retry
        primaryButton!!.onClick()
      }

      // Should go back to processing
      awaitUntilBody<LoadingSuccessBodyModel>(PARTNER_TRANSFER_LINK_PROCESSING)

      // Verify deeplink was called and flow completed
      deepLinkHandler.openDeeplinkCalls.awaitItem().shouldBe("partner://transfer?token=abc123")
      onCompleteCalls.awaitItem()
    }
  }

  test("calls onExit when cancel is clicked in confirmation sheet") {
    stateMachine.test(props) {
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onCancel()
      }

      onExitCalls.awaitItem()
    }
  }

  test("calls onExit when cancel button is clicked in error state") {
    val retryableError = ProcessTransferLinkError.Retryable(Error("Network error"))
    partnershipTransferLinkService.processTransferLinkResult = Err(retryableError)

    stateMachine.test(props) {
      // Loading and confirmation flow
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel> {
        onConfirm()
      }

      // Get to error state
      awaitUntilBody<FormBodyModel> {
        secondaryButton!!.onClick()
      }

      onExitCalls.awaitItem()
    }
  }

  test("can retry partner lookup when partner not found error occurs") {
    partnershipTransferLinkService.getPartnerInfoForPartnerResult =
      Err(GetPartnerInfoError.PartnerNotFound(Error("Partner not found")))

    stateMachine.test(props) {
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilBody<FormBodyModel> {
        // Set up success for retry
        partnershipTransferLinkService.getPartnerInfoForPartnerResult = Ok(
          PartnerInfo(
            logoUrl = null,
            logoBadgedUrl = null,
            name = "Test Partner",
            partnerId = PartnerId("test-partner")
          )
        )

        // Trigger retry
        primaryButton!!.onClick()
      }

      // Should go back to loading partner info
      awaitUntilSheet<TransferLinkLoadingPartnersBodyModel>(PARTNER_TRANSFER_LINK_RETRIEVING_PARTNER_INFO)
      awaitUntilSheet<PartnerTransferLinkConfirmationFormBodyModel>()
    }
  }
})

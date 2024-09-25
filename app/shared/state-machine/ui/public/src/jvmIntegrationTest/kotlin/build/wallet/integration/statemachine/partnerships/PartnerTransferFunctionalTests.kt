package build.wallet.integration.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiProps
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PartnerTransferFunctionalTests : FunSpec({
  context("Partnerships Transfer Flow") {
    val appTester = launchNewApp()
    val account = appTester.onboardFullAccountWithFakeHardware()
    var capturedRedirectionMethod: PartnerRedirectionMethod? = null
    var capturedTransaction: PartnershipTransaction? = null
    val transferUiProps = PartnershipsTransferUiProps(
      account = account,
      keybox = account.keybox,
      onBack = {},
      onAnotherWalletOrExchange = {},
      onPartnerRedirected = { redirectionMethod, transaction ->
        capturedRedirectionMethod = redirectionMethod
        capturedTransaction = transaction
      },
      onExit = {}
    )

    test("displays the expected partners") {
      appTester.app.addBitcoinUiStateMachine.partnershipsTransferUiStateMachine.test(
        props = transferUiProps,
        useVirtualTime = true
      ) {
        val sheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNERS_LIST
        }

        val body = sheetModel.body.shouldBeTypeOf<FormBodyModel>()
        assertEquals("Select a partner", body.toolbar?.middleAccessory?.title)

        val items = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
        assertEquals(2, items.size)
        assertEquals("Testnet Faucet", items[0].title)
        assertEquals("Another exchange or wallet", items[1].title)
      }
    }

    test("redirects correctly") {
      appTester.app.addBitcoinUiStateMachine.partnershipsTransferUiStateMachine.test(
        props = transferUiProps,
        useVirtualTime = true
      ) {
        val sheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNERS_LIST
        }

        val body = sheetModel.body.shouldBeTypeOf<FormBodyModel>()
        val partnerItem = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items.first()

        partnerItem.onClick?.invoke()

        awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNER_REDIRECTING
        }

        val expectedRedirectionMethod = PartnerRedirectionMethod.Web(
          urlString = "https://bitcoinfaucet.uo1.net/send.php",
          partnerInfo = PartnerInfo(
            name = "Testnet Faucet",
            logoUrl = null,
            partnerId = PartnerId("TestnetFaucet")
          )
        )
        assertEquals(expectedRedirectionMethod, capturedRedirectionMethod)
        assertNotNull(capturedTransaction)
      }
    }
  }
})

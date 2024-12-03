package build.wallet.integration.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.partnerships.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiProps
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.test.assertEquals

class PartnerPurchaseFunctionalTests : FunSpec({

  val onPartnerRedirectedCalls =
    turbines.create<Pair<PartnerRedirectionMethod, PartnershipTransaction>>("onPartnerRedirected")

  context("Partnerships purchase flow") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()
    val purchaseUiProps = PartnershipsPurchaseUiProps(
      onBack = {},
      selectedAmount = null,
      onPartnerRedirected = { redirectionMethod, transaction ->
        onPartnerRedirectedCalls.add(redirectionMethod to transaction)
      },
      onSelectCustomAmount = { _, _ -> },
      onExit = {}
    )

    test("displays the expected purchase options") {
      app.partnershipsPurchaseUiStateMachine.test(
        props = purchaseUiProps,
        useVirtualTime = true
      ) {
        val sheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS
        }

        val body = sheetModel.body.shouldBeInstanceOf<FormBodyModel>()
        assertEquals("Choose an amount", body.toolbar?.middleAccessory?.title)

        val items = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
        val expectedAmounts = listOf("$75", "$100", "$200", "$300", "$500", "...")
        assertEquals(expectedAmounts.size, items.size)
        items.forEachIndexed { index, item ->
          assertEquals(expectedAmounts[index], item.title)
        }
      }
    }

    test("displays the expected quotes") {
      app.partnershipsPurchaseUiStateMachine.test(
        props = purchaseUiProps,
        useVirtualTime = true
      ) {
        val amountsSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS
        }
        amountsSheetModel.body.shouldBeInstanceOf<FormBodyModel>().primaryButton?.onClick?.invoke()

        val quotesSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
        }

        val body = quotesSheetModel.body.shouldBeInstanceOf<FormBodyModel>()

        val items = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items

        assertEquals(1, items.size)
        assertEquals("Signet Faucet", items.first().title)
      }
    }

    test("redirects correctly") {
      app.partnershipsPurchaseUiStateMachine.test(
        props = purchaseUiProps,
        useVirtualTime = true
      ) {
        val amountsSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS
        }
        amountsSheetModel.body.shouldBeInstanceOf<FormBodyModel>().primaryButton?.onClick?.invoke()

        val quotesSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
        }

        val body = quotesSheetModel.body.shouldBeInstanceOf<FormBodyModel>()

        val quoteItems = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items

        quoteItems.first().onClick?.invoke()

        awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING
        }

        val expectedRedirectionMethod = PartnerRedirectionMethod.Web(
          urlString = "https://signetfaucet.com/",
          partnerInfo = PartnerInfo(
            name = "Signet Faucet",
            logoUrl = null,
            partnerId = PartnerId("SignetFaucet"),
            logoBadgedUrl = null
          )
        )

        onPartnerRedirectedCalls.awaitItem().should { (redirectionMethod, transaction) ->
          redirectionMethod.shouldBe(expectedRedirectionMethod)

          transaction.shouldBeTypeOf<PartnershipTransaction>()
          transaction.type.shouldBe(PartnershipTransactionType.PURCHASE)
          transaction.partnerInfo.shouldBe(expectedRedirectionMethod.partnerInfo)
        }
      }
    }
  }
})

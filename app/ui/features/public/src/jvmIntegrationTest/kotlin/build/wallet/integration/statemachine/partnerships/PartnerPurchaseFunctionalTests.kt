package build.wallet.integration.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiProps
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.tags.TestTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PartnerPurchaseFunctionalTests : FunSpec({
  tags(TestTag.ServerSmoke)

  val onAmountConfirmedCalls = turbines.create<FiatMoney>("onAmountConfirmed")
  val onPartnerRedirectedCalls =
    turbines.create<Pair<PartnerRedirectionMethod, PartnershipTransaction>>("onPartnerRedirected")

  context("Partnerships purchase flow") {
    val f8eEnvironment = getEnvironment()
    val bitcoinNetworkType = getBitcoinNetworkType(f8eEnvironment)
    val app = launchNewApp(bitcoinNetworkType = bitcoinNetworkType)
    app.onboardFullAccountWithFakeHardware()
    val expectedPartners = getExpectedPartners(f8eEnvironment)

    test("displays the expected purchase options") {
      val amountProps = PartnershipsPurchaseAmountUiProps(
        selectedAmount = null,
        onAmountConfirmed = { onAmountConfirmedCalls.add(it) },
        onSelectCustomAmount = { _, _ -> },
        onExit = {}
      )

      app.partnershipsPurchaseAmountUiStateMachine.test(props = amountProps) {
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
      val amount = FiatMoney.usd(10000)
      val quotesProps = PartnershipsPurchaseQuotesUiProps(
        purchaseAmount = amount,
        onPartnerRedirected = { redirectionMethod, transaction ->
          onPartnerRedirectedCalls.add(redirectionMethod to transaction)
        },
        onBack = {},
        onExit = {}
      )

      app.partnershipsPurchaseQuotesUiStateMachine.test(props = quotesProps) {
        val quotesSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
        }

        val body = quotesSheetModel.body.shouldBeInstanceOf<FormBodyModel>()

        val items = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items

        // Partners may be disabled via a feature flag, so assert there's at least 1 enabled partner
        assertTrue(items.size > 0, "Expected more than 1, got: ${items.map { it.title }}")

        assertNotNull(expectedPartners)
        assertTrue(
          items.all { item ->
            expectedPartners.map { it.name }.contains(item.title)
          },
          "All partners not in $expectedPartners, got: ${items.map { it.title }}"
        )
      }
    }

    test("redirects correctly") {
      val amount = FiatMoney.usd(10000)
      val quotesProps = PartnershipsPurchaseQuotesUiProps(
        purchaseAmount = amount,
        onPartnerRedirected = { redirectionMethod, transaction ->
          onPartnerRedirectedCalls.add(redirectionMethod to transaction)
        },
        onBack = {},
        onExit = {}
      )

      app.partnershipsPurchaseQuotesUiStateMachine.test(props = quotesProps) {
        val quotesSheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
        }

        val body = quotesSheetModel.body.shouldBeInstanceOf<FormBodyModel>()

        val quoteItems = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items

        quoteItems.forEach { quoteItem ->
          quoteItem.onClick?.invoke()

          awaitUntil {
            it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING
          }

          onPartnerRedirectedCalls.awaitItem().should { (redirectionMethod, transaction) ->
            assertNotNull(redirectionMethod)
            transaction.shouldBeTypeOf<PartnershipTransaction>()
            transaction.type.shouldBe(PartnershipTransactionType.PURCHASE)

            transaction.partnerInfo.name.shouldBeIn(expectedPartners.map { it.name })
          }
        }
      }
    }
  }
})

private fun getExpectedPartners(f8eEnvironment: F8eEnvironment): List<PartnerInfo> {
  return when (f8eEnvironment) {
    F8eEnvironment.Production -> listOf(
      CASH_APP,
      COINBASE,
      ROBINHOOD,
      MOONPAY,
      BLOCKCHAIN
    )
    F8eEnvironment.Staging -> listOf(
      CASH_APP,
      MOONPAY,
      STRIKE
    )
    else -> listOf(
      SIGNET_FAUCET
    )
  }
}

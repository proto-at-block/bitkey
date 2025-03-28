package build.wallet.integration.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.F8eEnvironment
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiProps
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.tags.TestTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PartnerTransferFunctionalTests : FunSpec({
  tags(TestTag.ServerSmoke)

  context("Partnerships Transfer Flow") {
    val f8eEnvironment = getEnvironment()
    val bitcoinNetworkType = getBitcoinNetworkType(f8eEnvironment)
    val app = launchNewApp(bitcoinNetworkType = bitcoinNetworkType)
    val account = app.onboardFullAccountWithFakeHardware()
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
    val expectedPartners = getExpectedPartners(f8eEnvironment)

    test("displays the expected partners") {
      app.partnershipsTransferUiStateMachine.test(props = transferUiProps) {
        val sheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNERS_LIST
        }

        val body = sheetModel.body.shouldBeInstanceOf<FormBodyModel>()
        body.header?.headline.shouldNotBeNull().shouldBe("Receive bitcoin from")

        val items = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items

        // Partners may be disabled via a feature flag, so assert there's at least 1 enabled partner
        assertTrue(items.size > 1, "Expected more than 1, got: ${items.map { it.title }}")

        assertNotNull(expectedPartners)
        assertTrue(
          items.dropLast(1).all { item ->
            expectedPartners.map { it.name }.contains(item.title)
          },
          "All partners not in $expectedPartners, got: ${items.map { it.title }}"
        )
        assertEquals("Another exchange or wallet", items.last().title)
      }
    }

    test("redirects correctly") {
      app.partnershipsTransferUiStateMachine.test(props = transferUiProps) {
        val sheetModel = awaitUntil {
          it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNERS_LIST
        }

        val body = sheetModel.body.shouldBeInstanceOf<FormBodyModel>()

        val partnerItems = body.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .dropLast(1)

        partnerItems.forEach { partnerItem ->
          partnerItem.onClick?.invoke()

          awaitUntil {
            it.body.eventTrackerScreenInfo?.eventTrackerScreenId == DepositEventTrackerScreenId.TRANSFER_PARTNER_REDIRECTING
          }

          assertNotNull(capturedRedirectionMethod)
          val partner =
            if (capturedRedirectionMethod is PartnerRedirectionMethod.Web) {
              (capturedRedirectionMethod as PartnerRedirectionMethod.Web).partnerInfo.name
            } else {
              (capturedRedirectionMethod as PartnerRedirectionMethod.Deeplink).partnerName
            }
          assertTrue(
            expectedPartners.map {
              it.name
            }.contains(partner),
            "Expected $partner to be in $expectedPartners"
          )
          assertNotNull(capturedTransaction)
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
      ROBINHOOD
    )
    F8eEnvironment.Staging -> listOf(
      CASH_APP
    )
    else -> listOf(
      TESTNET_FAUCET
    )
  }
}

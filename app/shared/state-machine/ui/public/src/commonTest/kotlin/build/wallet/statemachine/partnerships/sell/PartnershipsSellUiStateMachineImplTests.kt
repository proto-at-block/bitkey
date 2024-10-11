package build.wallet.statemachine.partnerships.sell

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetSaleQuoteListF8eClientMock
import build.wallet.f8e.partnerships.GetSellRedirectF8eClientMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransactionStatusRepositoryMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsSellUiStateMachineImplTests : FunSpec({
  // turbines
  val onBack = turbines.create<Unit>("on back calls")
  val onPartnerRedirectedCalls =
    turbines.create<PartnerRedirectionMethod>(
      "on partner redirected calls"
    )
  val getSaleQuoteListF8eClient = GetSaleQuoteListF8eClientMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val getSellRedirectF8eClient = GetSellRedirectF8eClientMock(turbines::create)
  val partnershipRepository = PartnershipTransactionStatusRepositoryMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls")
  )

  // state machine
  val stateMachine =
    PartnershipsSellOptionsUiStateMachineImpl(
      getSaleQuoteListF8eClient = getSaleQuoteListF8eClient,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      eventTracker = eventTracker,
      getSellRedirectF8eClient = getSellRedirectF8eClient,
      partnershipsRepository = partnershipRepository
    )

  val props =
    PartnershipsSellOptionsUiProps(
      keybox = KeyboxMock,
      onBack = {
        onBack.add(Unit)
      },
      onPartnerRedirected = { method, _ ->
        onPartnerRedirectedCalls.add(method)
      }
    )

  // tests

  test("load sell partners") {
    stateMachine.test(props) {
      getSaleQuoteListF8eClient.getSaleQuotesListCall.awaitItem()

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList.size.shouldBe(1)
        mainContentList[0].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items.count().shouldBe(2)
          listGroupModel.items[0].title.shouldBe("partner")
          listGroupModel.items[1].title.shouldBe("More partners coming soon...")
        }

        eventTracker.eventCalls.awaitItem().should {
          it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_SALE_PARTNER)
          it.context.should { context ->
            context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
            context.name.shouldBe("partner")
          }
        }
      }
    }
  }
})

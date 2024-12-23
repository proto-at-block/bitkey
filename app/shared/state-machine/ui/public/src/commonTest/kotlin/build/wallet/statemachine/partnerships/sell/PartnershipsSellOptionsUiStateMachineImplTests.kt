package build.wallet.statemachine.partnerships.sell

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetSaleQuoteListF8eClientMock
import build.wallet.f8e.partnerships.GetSellRedirectF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SellBitcoinQuotesEnabledFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransactionsServiceMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsSellOptionsUiStateMachineImplTests : FunSpec({
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
  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val sellBitcoinQuotesEnabledFeatureFlag = SellBitcoinQuotesEnabledFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )

  // state machine
  val stateMachine =
    PartnershipsSellOptionsUiStateMachineImpl(
      getSaleQuoteListF8eClient = getSaleQuoteListF8eClient,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      eventTracker = eventTracker,
      getSellRedirectF8eClient = getSellRedirectF8eClient,
      partnershipTransactionsService = partnershipTransactionsService,
      currencyConverter = CurrencyConverterFake(),
      moneyFormatter = MoneyDisplayFormatterFake,
      sellBitcoinQuotesEnabledFeatureFlag = sellBitcoinQuotesEnabledFeatureFlag
    )

  val props =
    PartnershipsSellOptionsUiProps(
      sellAmount = BitcoinMoney.Companion.sats(100_000),
      exchangeRates = emptyImmutableList(),
      keybox = KeyboxMock,
      onBack = {
        onBack.add(Unit)
      },
      onPartnerRedirected = { method, _ ->
        onPartnerRedirectedCalls.add(method)
      }
    )

  beforeEach {
    sellBitcoinQuotesEnabledFeatureFlag.reset()
  }

  // tests

  test("load sell partners") {
    sellBitcoinQuotesEnabledFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

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

  test("load sell quotes") {
    sellBitcoinQuotesEnabledFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      getSaleQuoteListF8eClient.getSaleQuotesListCall.awaitItem()

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitScreenWithBody<SellQuotesFormBodyModel> {
        this.mainContentList.size.shouldBe(1)
        mainContentList[0].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items.count().shouldBe(1)
          listGroupModel.items[0].title.shouldBe("partner")
          listGroupModel.items[0].sideText.shouldBe("$12.34")
          listGroupModel.items[0].secondarySideText.shouldBeNull()
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

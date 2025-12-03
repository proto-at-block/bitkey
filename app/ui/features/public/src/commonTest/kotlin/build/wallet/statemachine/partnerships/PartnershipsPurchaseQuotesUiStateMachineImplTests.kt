package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.CashAppFeePromotionFeatureFlag
import build.wallet.money.FiatMoney
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.*
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsPurchaseQuotesUiStateMachineImplTests : FunSpec({
  val partnershipPurchaseService = PartnershipPurchaseServiceFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val onPartnerRedirectedCalls =
    turbines.create<PartnerRedirectionMethod>("on partner redirected calls")
  val onBackCalls = turbines.create<Unit>("on back calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val currencyConverter = CurrencyConverterFake()

  val stateMachine = PartnershipsPurchaseQuotesUiStateMachineImpl(
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    partnershipPurchaseService = partnershipPurchaseService,
    partnershipTransactionsService = partnershipTransactionsService,
    eventTracker = eventTracker,
    currencyConverter = currencyConverter,
    exchangeRateService = ExchangeRateServiceFake(),
    cashAppFeePromotionFeatureFlag = CashAppFeePromotionFeatureFlag(FeatureFlagDaoFake())
  )

  fun props(purchaseAmount: FiatMoney = FiatMoney.usd(100.0)) =
    PartnershipsPurchaseQuotesUiProps(
      purchaseAmount = purchaseAmount,
      onPartnerRedirected = { method, _ -> onPartnerRedirectedCalls.add(method) },
      onBack = { onBackCalls.add(Unit) },
      onExit = { onExitCalls.add(Unit) }
    )

  beforeTest {
    partnershipPurchaseService.reset()
    partnershipTransactionsService.reset()
    currencyConverter.conversionRate = 3.0
  }

  test("partnerships purchase quotes") {
    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      // show purchase quotes
      awaitBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems.size.shouldBe(1)
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("partner")
          sideText.shouldBe("$0.01")
          secondarySideText.shouldBe("195,701 sats")
        }
      }

      eventTracker.eventCalls.awaitItem().should {
        it.action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)
        it.context.should { context ->
          context.shouldBeTypeOf<PartnerEventTrackerScreenIdContext>()
          context.name.shouldBe("partner")
        }
      }
    }
  }

  test("Previously used partner at top of list") {
    partnershipTransactionsService.previouslyUsedPartnerIds.value =
      listOf(PartnerId("used-partner"))
    val quote = PurchaseQuote(
      fiatCurrency = "USD",
      cryptoAmount = 0.00195701,
      networkFeeCrypto = 0.0002710900770218228,
      networkFeeFiat = 11.87,
      cryptoPrice = 43786.18402563094,
      partnerInfo = PartnerInfo(
        name = "partner",
        logoUrl = "https://logo.url.example.com",
        partnerId = PartnerId("partner"),
        logoBadgedUrl = "https://badged-logo.url.example.com"
      ),
      userFeeFiat = 0.0,
      quoteId = "quoteId"
    )
    partnershipPurchaseService.purchaseQuotes = Ok(
      listOf(
        quote,
        quote.copy(
          partnerInfo = quote.partnerInfo.copy(
            name = "previously-used-partner",
            partnerId = PartnerId("used-partner")
          )
        )
      )
    )

    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      // show purchase quotes
      awaitBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems.size.shouldBe(2)
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("previously-used-partner")
          secondaryText.shouldNotBeNull()
        }
        listItems[1].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("partner")
        }
      }

      repeat(2) {
        eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)
      }
    }
  }

  test("Sorted by crypto amount") {
    partnershipTransactionsService.previouslyUsedPartnerIds.value = listOf(
      PartnerId("used-1"),
      PartnerId("used-2")
    )
    val quote = PurchaseQuote(
      fiatCurrency = "USD",
      cryptoAmount = 1.0,
      networkFeeCrypto = 0.0002710900770218228,
      networkFeeFiat = 11.87,
      cryptoPrice = 43786.18402563094,
      partnerInfo = PartnerInfo(
        name = "partner-1",
        logoUrl = "https://logo.url.example.com",
        partnerId = PartnerId("partner-1"),
        logoBadgedUrl = "https://badged-logo.url.example.com"
      ),
      userFeeFiat = 0.0,
      quoteId = "quoteId"
    )
    partnershipPurchaseService.purchaseQuotes = Ok(
      listOf(
        quote,
        quote.copy(
          partnerInfo = quote.partnerInfo.copy(
            name = "partner-2",
            partnerId = PartnerId("partner-2")
          ),
          cryptoAmount = 2.0
        ),
        quote.copy(
          partnerInfo = quote.partnerInfo.copy(name = "used-1", partnerId = PartnerId("used-1")),
          cryptoAmount = .1
        ),
        quote.copy(
          partnerInfo = quote.partnerInfo.copy(name = "used-2", partnerId = PartnerId("used-2")),
          cryptoAmount = .2
        )
      )
    )

    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      // show purchase quotes
      awaitBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems.size.shouldBe(4)
        listItems[0].shouldBeTypeOf<ListItemModel>().apply { title.shouldBe("used-2") }
        listItems[1].shouldBeTypeOf<ListItemModel>().apply { title.shouldBe("used-1") }
        listItems[2].shouldBeTypeOf<ListItemModel>().apply { title.shouldBe("partner-2") }
        listItems[3].shouldBeTypeOf<ListItemModel>().apply { title.shouldBe("partner-1") }
      }

      repeat(4) {
        eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)
      }
    }
  }

  test("partnerships purchase redirect") {
    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      // show purchase quotes
      awaitBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          // tap quote
          onClick.shouldNotBeNull().invoke()
        }
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)

      // load redirect info
      awaitBody<LoadingSuccessBodyModel>()

      // redirecting screen (shown after redirect info is loaded)
      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(PURCHASE_PARTNER_REDIRECTING)

        onPartnerRedirectedCalls.awaitItem().shouldBe(
          PartnerRedirectionMethod.Web(
            urlString = "https://fake-partner.com/purchase",
            partnerInfo = PartnerInfoFake
          )
        )
      }
    }
  }

  test("purchase quotes with no fiat currency conversion") {
    currencyConverter.conversionRate = null

    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      // show purchase quotes
      awaitBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("partner")
          sideText.shouldBe("195,701 sats")
          secondarySideText.shouldBeNull()
        }
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)
    }
  }

  test("back navigation from quotes screen") {
    stateMachine.test(props()) {
      // load purchase quotes
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      eventTracker.eventCalls.awaitItem()

      // Verify onBack was called
      onBackCalls.awaitItem()
    }
  }
})

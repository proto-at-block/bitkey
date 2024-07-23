package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.*
import build.wallet.money.FiatMoney
import build.wallet.money.currency.GBP
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.*
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.address.KeyboxAddressDataMock
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachineImpl
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsPurchaseUiStateMachineImplTests : FunSpec({
  val getPurchaseOptionsF8eClient = GetPurchaseOptionsF8eClientMock(turbines::create)
  val getPurchaseQuoteListF8eClient = GetPurchaseQuoteListF8eClientMock(turbines::create)
  val getPurchaseRedirectF8eClient = GetPurchaseRedirectF8eClientMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val onPartnerRedirectedCalls =
    turbines.create<PartnerRedirectionMethod>(
      "on partner redirected calls"
    )
  val onSelectCustomAmount =
    turbines.create<Pair<FiatMoney, FiatMoney>>(
      "on select custom amount"
    )
  val partnershipRepositoryMock = PartnershipTransactionStatusRepositoryMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val currencyConverter = CurrencyConverterFake()

  val stateMachine = PartnershipsPurchaseUiStateMachineImpl(
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    getPurchaseOptionsF8eClient = getPurchaseOptionsF8eClient,
    getPurchaseQuoteListF8eClient = getPurchaseQuoteListF8eClient,
    getPurchaseRedirectF8eClient = getPurchaseRedirectF8eClient,
    partnershipsRepository = partnershipRepositoryMock,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    eventTracker = eventTracker,
    currencyConverter = currencyConverter,
    exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create)
  )

  fun props(selectedAmount: FiatMoney? = null) =
    PartnershipsPurchaseUiProps(
      keybox = KeyboxMock,
      generateAddress = KeyboxAddressDataMock.generateAddress,
      selectedAmount = selectedAmount,
      onPartnerRedirected = { method, _ -> onPartnerRedirectedCalls.add(method) },
      onSelectCustomAmount = { min, max -> onSelectCustomAmount.add(min to max) },
      onBack = {},
      onExit = {}
    )

  afterTest {
    fiatCurrencyPreferenceRepository.reset()
    getPurchaseQuoteListF8eClient.reset()
    currencyConverter.conversionRate = 3.0
  }

  test("no partnerships purchase options") {
    fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = GBP
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        header?.headline.shouldBe("New Partners Coming Soon")
        header?.sublineModel?.string.shouldBe("Bitkey is actively seeking partnerships with local exchanges to facilitate bitcoin purchases. Until then, you can add bitcoin using the receive button.")
      }
    }
  }

  test("partnerships purchase options") {
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      // show purchase amounts
      awaitSheetWithBody<FormBodyModel> {
        toolbar?.middleAccessory?.title.shouldBe("Choose an amount")
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[0].title.shouldBe("$10")
        items[0].selected.shouldBe(false)
        items[1].title.shouldBe("$25")
        items[1].selected.shouldBe(false)
        items[2].title.shouldBe("$50")
        items[2].selected.shouldBe(false)
        items[3].title.shouldBe("$100")
        items[3].selected.shouldBe(true)
        items[4].title.shouldBe("$200")
        items[4].selected.shouldBe(false)
        items[5].title.shouldBe("...")
        items[5].selected.shouldBe(false)

        // tap $100 to unselect
        items[3].onClick.shouldNotBeNull().invoke()
      }

      awaitSheetWithBody<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[3].title.shouldBe("$100")
        items[3].selected.shouldBe(false)

        // tap $200
        items[4].onClick.shouldNotBeNull().invoke()
      }

      awaitSheetWithBody<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[4].title.shouldBe("$200")
        items[4].selected.shouldBe(true)
      }
    }
  }

  test("partnerships purchase quotes") {
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
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
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      partnershipRepositoryMock.previouslyUsedPartnerIds.value = listOf(PartnerId("used-partner"))
      val quote = Quote(
        fiatCurrency = "USD",
        cryptoAmount = 0.00195701,
        networkFeeCrypto = 0.0002710900770218228,
        networkFeeFiat = 11.87,
        cryptoPrice = 43786.18402563094,
        partnerInfo =
          PartnerInfo(
            name = "partner",
            logoUrl = "https://logo.url.example.com",
            partnerId = PartnerId("partner")
          ),
        userFeeFiat = 0.0,
        quoteId = "quoteId"
      )
      getPurchaseQuoteListF8eClient.quotesListResult =
        Ok(
          GetPurchaseQuoteListF8eClient.Success(
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
        )
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
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
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      partnershipRepositoryMock.previouslyUsedPartnerIds.value = listOf(
        PartnerId("used-1"),
        PartnerId("used-2")
      )
      val quote = Quote(
        fiatCurrency = "USD",
        cryptoAmount = 1.0,
        networkFeeCrypto = 0.0002710900770218228,
        networkFeeFiat = 11.87,
        cryptoPrice = 43786.18402563094,
        partnerInfo =
          PartnerInfo(
            name = "partner-1",
            logoUrl = "https://logo.url.example.com",
            partnerId = PartnerId("partner-1")
          ),
        userFeeFiat = 0.0,
        quoteId = "quoteId"
      )
      getPurchaseQuoteListF8eClient.quotesListResult =
        Ok(
          GetPurchaseQuoteListF8eClient.Success(
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
                partnerInfo = quote.partnerInfo.copy(
                  name = "used-1",
                  partnerId = PartnerId("used-1")
                ),
                cryptoAmount = .1
              ),
              quote.copy(
                partnerInfo = quote.partnerInfo.copy(
                  name = "used-2",
                  partnerId = PartnerId("used-2")
                ),
                cryptoAmount = .2
              )
            )
          )
        )
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems.size.shouldBe(4)
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("used-2")
        }
        listItems[1].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("used-1")
        }
        listItems[2].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("partner-2")
        }
        listItems[3].shouldBeTypeOf<ListItemModel>().apply {
          title.shouldBe("partner-1")
        }
      }

      repeat(4) {
        eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)
      }
    }
  }

  test("partnerships purchase redirect") {
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
        id.shouldBe(PARTNER_QUOTES_LIST)
        val listItems = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        listItems[0].shouldBeTypeOf<ListItemModel>().apply {
          // tap quote
          onClick.shouldNotBeNull().invoke()
        }
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE)

      // load redirect info
      getPurchaseRedirectF8eClient.getPurchasePartnersRedirectCall.awaitItem()
      awaitSheetWithBody<FormBodyModel>()

      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<Loader>()

        partnershipRepositoryMock.createCalls.awaitItem().should { (partnerInfo, type) ->
          type.shouldBe(PartnershipTransactionType.PURCHASE)
          partnerInfo.shouldBe(
            PartnerInfo(
              logoUrl = "https://logo.url.example.com",
              name = "partner",
              partnerId = PartnerId("partner")
            )
          )
        }
        onPartnerRedirectedCalls.awaitItem().shouldBe(
          PartnerRedirectionMethod.Web(
            "http://example.com/redirect_url",
            partnerInfo = PartnerInfo(
              logoUrl = "https://logo.url.example.com",
              name = "partner",
              partnerId = PartnerId("partner")
            )
          )
        )
      }
    }
  }

  test("resume partnerships purchase flow with selected amount") {
    val selectedAmount = FiatMoney.usd(123.0)
    stateMachine.test(props(selectedAmount = selectedAmount)) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
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

  test("select custom amount") {
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[5].title.shouldBe("...")
        items[5].onClick.shouldNotBeNull().invoke()
        onSelectCustomAmount.awaitItem().shouldBe(
          FiatMoney.usd(10.0) to FiatMoney.usd(500.0)
        )
      }
    }
  }

  test("purchase quotes with no fiat currency conversion") {
    currencyConverter.conversionRate = null
    stateMachine.test(props()) {
      // load purchase amounts
      getPurchaseOptionsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      awaitSheetWithBody<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
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
})

private suspend fun StateMachineTester<PartnershipsPurchaseUiProps, SheetModel>.awaitLoader() {
  awaitSheetWithBody<FormBodyModel> {
    mainContentList[0].shouldBeTypeOf<Loader>()
  }
}

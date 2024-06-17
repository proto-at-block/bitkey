package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetPurchaseOptionsF8eClientMock
import build.wallet.f8e.partnerships.GetPurchaseQuoteListF8eClientMock
import build.wallet.f8e.partnerships.GetPurchaseRedirectF8eClientMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.GBP
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransactionStatusRepositoryMock
import build.wallet.partnerships.PartnershipTransactionType
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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsPurchaseUiStateMachineImplTests : FunSpec({
  val getPurchaseOptionsF8eClient = GetPurchaseOptionsF8eClientMock(turbines::create)
  val getPurchaseQuoteListF8eClient = GetPurchaseQuoteListF8eClientMock(turbines::create)
  val getPurchaseRedirectF8eClient = GetPurchaseRedirectF8eClientMock(turbines::create)
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
    fetchMostRecentCalls = turbines.create("fetch most recent calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine = PartnershipsPurchaseUiStateMachineImpl(
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    getPurchaseOptionsF8eClient = getPurchaseOptionsF8eClient,
    getPurchaseQuoteListF8eClient = getPurchaseQuoteListF8eClient,
    getPurchaseRedirectF8eClient = getPurchaseRedirectF8eClient,
    partnershipsRepository = partnershipRepositoryMock,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
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

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
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
          sideText.shouldBe("195,701 sats")
        }
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
          sideText.shouldBe("195,701 sats")
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
})

private suspend fun StateMachineTester<PartnershipsPurchaseUiProps, SheetModel>.awaitLoader() {
  awaitSheetWithBody<FormBodyModel> {
    mainContentList[0].shouldBeTypeOf<Loader>()
  }
}

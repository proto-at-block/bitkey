package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.*
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnershipTransactionStatusRepositoryMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.address.KeyboxAddressDataMock
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachineImpl
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class AddBitcoinUiStateMachineImplTests : FunSpec({
  // turbines
  val getPurchaseAmountsF8eClient = GetPurchaseOptionsF8eClientMock(turbines::create)
  val getPurchaseQuoteListF8eClient = GetPurchaseQuoteListF8eClientMock(turbines::create)
  val getPurchaseRedirectF8eClient = GetPurchaseRedirectF8eClientMock(turbines::create)
  val getTransferPartnerListF8eClient = GetTransferPartnerListF8eClientMock(turbines::create)
  val getTransferRedirectF8eClient = GetTransferRedirectF8eClientMock(turbines::create)
  val partnershipRepositoryMock = PartnershipTransactionStatusRepositoryMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  // state machines
  val stateMachine =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine =
        PartnershipsTransferUiStateMachineImpl(
          getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
          getTransferRedirectF8eClient = getTransferRedirectF8eClient,
          partnershipsRepository = partnershipRepositoryMock,
          eventTracker = eventTracker
        ),
      partnershipsPurchaseUiStateMachine = PartnershipsPurchaseUiStateMachineImpl(
        moneyDisplayFormatter = MoneyDisplayFormatterFake,
        getPurchaseOptionsF8eClient = getPurchaseAmountsF8eClient,
        getPurchaseQuoteListF8eClient = getPurchaseQuoteListF8eClient,
        getPurchaseRedirectF8eClient = getPurchaseRedirectF8eClient,
        partnershipsRepository = partnershipRepositoryMock,
        fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
        eventTracker = eventTracker,
        currencyConverter = CurrencyConverterFake(),
        exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create)
      )
    )

  fun props(purchaseAmount: FiatMoney? = null): AddBitcoinUiProps =
    AddBitcoinUiProps(
      purchaseAmount = purchaseAmount,
      onAnotherWalletOrExchange = {},
      onPartnerRedirected = { _, _ -> },
      onExit = {},
      keybox = KeyboxMock,
      generateAddress = KeyboxAddressDataMock.generateAddress,
      onSelectCustomAmount = { _, _ -> }
    )

  // tests

  test("show purchase or transfer") {
    stateMachine.test(props()) {
      // Show purchase or transfer flow
      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<ListGroup>()) {
          listGroupModel.items.count().shouldBe(2)
          listGroupModel.items[0].title.shouldBe("Purchase")
          listGroupModel.items[1].title.shouldBe("Transfer")
        }
      }
    }
  }

  test("resume purchase flow") {
    val purchaseAmount = FiatMoney.Companion.usd(123.0)
    stateMachine.test(props(purchaseAmount = purchaseAmount)) {
      // load purchase amounts
      getPurchaseAmountsF8eClient.getPurchaseOptionsCall.awaitItem()
      awaitLoader()

      // load purchase quotes
      getPurchaseQuoteListF8eClient.getPurchaseQuotesListCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
        id.shouldBe(DepositEventTrackerScreenId.PARTNER_QUOTES_LIST)
      }

      eventTracker.eventCalls.awaitItem()
    }
  }
})

private suspend fun StateMachineTester<AddBitcoinUiProps, SheetModel>.awaitLoader() {
  awaitSheetWithBody<FormBodyModel> {
    mainContentList[0].shouldBeTypeOf<Loader>()
  }
}

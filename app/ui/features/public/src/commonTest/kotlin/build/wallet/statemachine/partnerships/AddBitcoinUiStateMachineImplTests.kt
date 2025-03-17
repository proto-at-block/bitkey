package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClientMock
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClientMock
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnershipPurchaseServiceFake
import build.wallet.partnerships.PartnershipTransactionsServiceMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.partnerships.purchase.LoadingBodyModel
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.SelectPartnerQuoteBodyModel
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import build.wallet.statemachine.ui.awaitSheet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AddBitcoinUiStateMachineImplTests : FunSpec({
  val partnershipPurchaseService = PartnershipPurchaseServiceFake()
  val getTransferPartnerListF8eClient = GetTransferPartnerListF8eClientMock(turbines::create)
  val getTransferRedirectF8eClient = GetTransferRedirectF8eClientMock(turbines::create)
  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val bitcoinAddressService = BitcoinAddressServiceFake()

  // state machines
  val stateMachine =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine =
        PartnershipsTransferUiStateMachineImpl(
          getTransferPartnerListF8eClient = getTransferPartnerListF8eClient,
          getTransferRedirectF8eClient = getTransferRedirectF8eClient,
          partnershipTransactionsService = partnershipTransactionsService,
          eventTracker = eventTracker,
          bitcoinAddressService = bitcoinAddressService
        ),
      partnershipsPurchaseUiStateMachine = PartnershipsPurchaseUiStateMachineImpl(
        moneyDisplayFormatter = MoneyDisplayFormatterFake,
        partnershipPurchaseService = partnershipPurchaseService,
        partnershipTransactionsService = partnershipTransactionsService,
        fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
        eventTracker = eventTracker,
        currencyConverter = CurrencyConverterFake(),
        exchangeRateService = ExchangeRateServiceFake()
      )
    )

  fun props(purchaseAmount: FiatMoney? = null): AddBitcoinUiProps =
    AddBitcoinUiProps(
      account = FullAccountMock,
      onAnotherWalletOrExchange = {},
      onPartnerRedirected = { _, _ -> },
      onExit = {},
      keybox = KeyboxMock,
      onSelectCustomAmount = { _, _ -> },
      initialState = if (purchaseAmount != null) {
        AddBitcoinBottomSheetDisplayState.PurchasingUiState(purchaseAmount)
      } else {
        AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState
      }
    )

  // tests

  test("show purchase or transfer") {
    stateMachine.testWithVirtualTime(props()) {
      // Show purchase or transfer flow
      awaitSheet<BuyOrTransferBodyModel> {
        listGroupModel.items.count().shouldBe(2)
        listGroupModel.items[0].title.shouldBe("Purchase")
        listGroupModel.items[1].title.shouldBe("Transfer")
      }
    }
  }

  test("resume purchase flow") {
    val purchaseAmount = FiatMoney.Companion.usd(123.0)
    stateMachine.testWithVirtualTime(props(purchaseAmount = purchaseAmount)) {
      // load purchase amounts
      awaitSheet<LoadingBodyModel>()

      // load purchase quotes
      awaitSheet<LoadingBodyModel>()

      // show purchase quotes
      awaitSheet<SelectPartnerQuoteBodyModel>()

      eventTracker.eventCalls.awaitItem()
    }
  }
})

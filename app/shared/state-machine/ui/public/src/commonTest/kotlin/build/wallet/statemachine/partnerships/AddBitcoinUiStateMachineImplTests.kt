package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetPurchaseOptionsServiceMock
import build.wallet.f8e.partnerships.GetPurchaseQuoteListServiceServiceMock
import build.wallet.f8e.partnerships.GetPurchaseRedirectServiceMock
import build.wallet.f8e.partnerships.GetTransferPartnerListServiceMock
import build.wallet.f8e.partnerships.GetTransferRedirectServiceMock
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
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
  val getPurchaseAmountsServiceMock = GetPurchaseOptionsServiceMock(turbines::create)
  val getPurchaseQuoteListServiceMock = GetPurchaseQuoteListServiceServiceMock(turbines::create)
  val getPurchaseRedirectServiceMock = GetPurchaseRedirectServiceMock(turbines::create)
  val getTransferPartnerListService = GetTransferPartnerListServiceMock(turbines::create)
  val getTransferRedirectService = GetTransferRedirectServiceMock(turbines::create)
  val partnershipRepositoryMock = PartnershipTransactionStatusRepositoryMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls")
  )
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  // state machines
  val stateMachine =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine =
        PartnershipsTransferUiStateMachineImpl(
          getTransferPartnerListService = getTransferPartnerListService,
          getTransferRedirectService = getTransferRedirectService,
          partnershipsRepository = partnershipRepositoryMock
        ),
      partnershipsPurchaseUiStateMachine = PartnershipsPurchaseUiStateMachineImpl(
        moneyDisplayFormatter = MoneyDisplayFormatterFake,
        getPurchaseOptionsService = getPurchaseAmountsServiceMock,
        getPurchaseQuoteListService = getPurchaseQuoteListServiceMock,
        getPurchaseRedirectService = getPurchaseRedirectServiceMock,
        partnershipsRepository = partnershipRepositoryMock,
        fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
      )
    )

  fun props(purchaseAmount: FiatMoney? = null): AddBitcoinUiProps =
    AddBitcoinUiProps(
      purchaseAmount = purchaseAmount,
      onAnotherWalletOrExchange = {},
      onPartnerRedirected = {},
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
      getPurchaseAmountsServiceMock.getPurchaseOptionsServiceCall.awaitItem()
      awaitLoader()

      // load purchase quotes
      getPurchaseQuoteListServiceMock.getPurchaseQuotesListServiceCall.awaitItem()
      awaitLoader()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
        id.shouldBe(DepositEventTrackerScreenId.PARTNER_QUOTES_LIST)
      }
    }
  }
})

private suspend fun StateMachineTester<AddBitcoinUiProps, SheetModel>.awaitLoader() {
  awaitSheetWithBody<FormBodyModel> {
    mainContentList[0].shouldBeTypeOf<Loader>()
  }
}

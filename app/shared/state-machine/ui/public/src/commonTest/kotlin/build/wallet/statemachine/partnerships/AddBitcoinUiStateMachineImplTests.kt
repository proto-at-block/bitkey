package build.wallet.statemachine.partnerships

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.deposit.PurchaseFlowIsEnabledFeatureFlag
import build.wallet.f8e.partnerships.GetPurchaseOptionsServiceMock
import build.wallet.f8e.partnerships.GetPurchaseQuoteListServiceServiceMock
import build.wallet.f8e.partnerships.GetPurchaseRedirectServiceMock
import build.wallet.f8e.partnerships.GetTransferPartnerListServiceMock
import build.wallet.f8e.partnerships.GetTransferRedirectServiceMock
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
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
  val appSpendingWalletProviderMock =
    AppSpendingWalletProviderMock(SpendingWalletMock(turbines::create))
  val getTransferPartnerListService = GetTransferPartnerListServiceMock(turbines::create)
  val getTransferRedirectService = GetTransferRedirectServiceMock(turbines::create)

  // state machines
  val stateMachineWithPurchaseFlow =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine =
        PartnershipsTransferUiStateMachineImpl(
          getTransferPartnerListService = getTransferPartnerListService,
          getTransferRedirectService = getTransferRedirectService,
          appSpendingWalletProvider = appSpendingWalletProviderMock
        ),
      purchaseFlowIsEnabledFeatureFlag =
        PurchaseFlowIsEnabledFeatureFlag(
          featureFlagDao = FeatureFlagDaoMock(),
          value = true
        ),
      partnershipsPurchaseUiStateMachine =
        PartnershipsPurchaseUiStateMachineImpl(
          moneyDisplayFormatter = MoneyDisplayFormatterFake,
          getPurchaseOptionsService = getPurchaseAmountsServiceMock,
          getPurchaseQuoteListService = getPurchaseQuoteListServiceMock,
          getPurchaseRedirectService = getPurchaseRedirectServiceMock,
          appSpendingWalletProvider = appSpendingWalletProviderMock
        )
    )

  /*
   * Since the `purchaseFlowIsEnabledFeatureFlag` is default off this is the standard
   * form of the state machine we will test
   */
  val stateMachine =
    AddBitcoinUiStateMachineImpl(
      partnershipsTransferUiStateMachine =
        PartnershipsTransferUiStateMachineImpl(
          getTransferPartnerListService = getTransferPartnerListService,
          getTransferRedirectService = getTransferRedirectService,
          appSpendingWalletProvider = appSpendingWalletProviderMock
        ),
      purchaseFlowIsEnabledFeatureFlag =
        PurchaseFlowIsEnabledFeatureFlag(
          featureFlagDao = FeatureFlagDaoMock()
        ),
      partnershipsPurchaseUiStateMachine =
        PartnershipsPurchaseUiStateMachineImpl(
          moneyDisplayFormatter = MoneyDisplayFormatterFake,
          getPurchaseOptionsService = getPurchaseAmountsServiceMock,
          getPurchaseQuoteListService = getPurchaseQuoteListServiceMock,
          getPurchaseRedirectService = getPurchaseRedirectServiceMock,
          appSpendingWalletProvider = appSpendingWalletProviderMock
        )
    )

  fun props(purchaseAmount: FiatMoney? = null): AddBitcoinUiProps =
    AddBitcoinUiProps(
      purchaseAmount = purchaseAmount,
      onAnotherWalletOrExchange = {},
      onPartnerRedirected = {},
      onExit = {},
      account = FullAccountMock,
      fiatCurrency = USD,
      onSelectCustomAmount = { _, _ -> }
    )

  // tests

  test("show transfer flow") {
    stateMachine.test(props()) {
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()

      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<Loader>()
      }

      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<ListGroup>()) {
          listGroupModel.items.count().shouldBe(3)
          listGroupModel.items[0].title.shouldBe("Partner 1")
          listGroupModel.items[1].title.shouldBe("Partner 2")
          listGroupModel.items[2].title.shouldBe("Another exchange or wallet")
        }
      }
    }
  }

  test("show purchase or transfer - flag on") {
    stateMachineWithPurchaseFlow.test(props()) {
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
    stateMachineWithPurchaseFlow.test(props(purchaseAmount = purchaseAmount)) {
      // load purchase amounts
      getPurchaseAmountsServiceMock.getPurchaseOptionsServiceCall.awaitItem()

      // load purchase quotes
      getPurchaseQuoteListServiceMock.getPurchaseQuotesListServiceCall.awaitItem()
      awaitSheetWithBody<FormBodyModel>()

      // show purchase quotes
      awaitSheetWithBody<FormBodyModel> {
        id.shouldBe(DepositEventTrackerScreenId.PARTNER_QUOTES_LIST)
      }
    }
  }
})

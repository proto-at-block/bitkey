package build.wallet.statemachine.partnerships

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.partnerships.PartnershipPurchaseServiceFake
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.SheetStateMachineMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiStateMachine
import build.wallet.statemachine.ui.awaitSheet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AddBitcoinUiStateMachineImplTests : FunSpec({
  val partnershipPurchaseService = PartnershipPurchaseServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val onPurchaseAmountConfirmedCalls = turbines.create<FiatMoney>("onPurchaseAmountConfirmed calls")

  val stateMachine = AddBitcoinUiStateMachineImpl(
    partnershipsPurchaseAmountUiStateMachine = object : PartnershipsPurchaseAmountUiStateMachine,
      SheetStateMachineMock<PartnershipsPurchaseAmountUiProps>(id = "partnerships-purchase-amount") {}
  )

  fun props(purchaseAmount: FiatMoney? = null): AddBitcoinUiProps =
    AddBitcoinUiProps(
      account = FullAccountMock,
      onTransfer = {},
      onExit = {},
      keybox = KeyboxMock,
      onSelectCustomAmount = { _, _ -> },
      initialState = if (purchaseAmount != null) {
        AddBitcoinBottomSheetDisplayState.PurchasingUiState(purchaseAmount)
      } else {
        AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState
      },
      onPurchaseAmountConfirmed = { amount ->
        onPurchaseAmountConfirmedCalls.add(amount)
      }
    )

  beforeTest {
    partnershipPurchaseService.reset()
    fiatCurrencyPreferenceRepository.reset()
  }

  test("show purchase or transfer") {
    stateMachine.test(props()) {
      // Show purchase or transfer flow
      awaitSheet<BuyOrTransferBodyModel> {
        listGroupModel.items.count().shouldBe(2)
        listGroupModel.items[0].title.shouldBe("Purchase")
        listGroupModel.items[1].title.shouldBe("Transfer")
      }
    }
  }

  test("shows purchase amount selection when initialized with purchase amount") {
    val purchaseAmount = FiatMoney.usd(100.0)

    stateMachine.test(props(purchaseAmount = purchaseAmount)) {
      with(awaitItem()) {
        shouldBeInstanceOf<SheetModel>()
          .body.shouldBeInstanceOf<BodyModelMock<PartnershipsPurchaseAmountUiProps>>()
          .latestProps.apply {
            selectedAmount.shouldBe(purchaseAmount)
          }
      }
    }
  }
})

package build.wallet.statemachine.partnerships

import build.wallet.coroutines.turbine.turbines
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnershipPurchaseService.NoPurchaseOptionsError
import build.wallet.partnerships.PartnershipPurchaseServiceFake
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseAmountUiStateMachineImpl
import build.wallet.statemachine.partnerships.purchase.SelectPurchaseAmountBodyModel
import build.wallet.statemachine.ui.awaitSheet
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsPurchaseAmountUiStateMachineImplTests : FunSpec({
  val partnershipPurchaseService = PartnershipPurchaseServiceFake()
  val onAmountConfirmedCalls = turbines.create<FiatMoney>("on amount confirmed calls")
  val onSelectCustomAmountCalls =
    turbines.create<Pair<FiatMoney, FiatMoney>>("on select custom amount calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine = PartnershipsPurchaseAmountUiStateMachineImpl(
    moneyDisplayFormatter = MoneyDisplayFormatterFake,
    partnershipPurchaseService = partnershipPurchaseService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
  )

  fun props(selectedAmount: FiatMoney? = null) =
    PartnershipsPurchaseAmountUiProps(
      selectedAmount = selectedAmount,
      onAmountConfirmed = { amount -> onAmountConfirmedCalls.add(amount) },
      onSelectCustomAmount = { min, max -> onSelectCustomAmountCalls.add(min to max) },
      onExit = { onExitCalls.add(Unit) }
    )

  beforeTest {
    partnershipPurchaseService.reset()
    fiatCurrencyPreferenceRepository.reset()
  }

  test("no partnerships purchase options") {
    partnershipPurchaseService.suggestedPurchaseAmounts = Err(NoPurchaseOptionsError("card"))
    stateMachine.test(props()) {
      // load purchase amounts
      awaitLoader()

      awaitSheet<FormBodyModel> {
        header?.headline.shouldBe("New Partners Coming Soon")
        header?.sublineModel?.string.shouldBe("Bitkey is actively seeking partnerships with local exchanges to facilitate bitcoin purchases. Until then, you can add bitcoin using the receive button.")
      }
    }
  }

  test("partnerships purchase options") {
    stateMachine.test(props()) {
      // load purchase amounts
      awaitLoader()

      // show purchase amounts
      awaitSheet<FormBodyModel> {
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

      awaitSheet<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[3].title.shouldBe("$100")
        items[3].selected.shouldBe(false)

        // tap $200
        items[4].onClick.shouldNotBeNull().invoke()
      }

      awaitSheet<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[4].title.shouldBe("$200")
        items[4].selected.shouldBe(true)
      }
    }
  }

  test("selecting next triggers onAmountConfirmed") {
    stateMachine.test(props()) {
      // load purchase amounts
      awaitLoader()

      awaitSheet<FormBodyModel> {
        // tap next with default selection ($100)
        primaryButton?.onClick.shouldNotBeNull().invoke()
      }

      // Verify onAmountConfirmed was called with the selected amount
      onAmountConfirmedCalls.awaitItem().shouldBe(FiatMoney.usd(100.0))
    }
  }

  test("resume with valid selected amount shows purchase amounts with amount selected") {
    val selectedAmount = FiatMoney.usd(200.0)
    stateMachine.test(props(selectedAmount = selectedAmount)) {
      // load purchase amounts
      awaitLoader()

      // show purchase amounts with selected amount
      awaitSheet<SelectPurchaseAmountBodyModel> {
        this.selectedAmount.shouldBe(selectedAmount)
        toolbar?.middleAccessory?.title.shouldBe("Choose an amount")
        primaryButton?.isEnabled.shouldBe(true)
      }
    }
  }

  test("select custom amount") {
    stateMachine.test(props()) {
      // load purchase amounts
      awaitLoader()

      awaitSheet<FormBodyModel> {
        val items = mainContentList[0].shouldBeTypeOf<ListGroup>().listGroupModel.items
        items[5].title.shouldBe("...")
        items[5].onClick.shouldNotBeNull().invoke()
        onSelectCustomAmountCalls.awaitItem().shouldBe(
          FiatMoney.usd(10.0) to FiatMoney.usd(500.0)
        )
      }
    }
  }

  test("back navigation from SelectPurchaseAmountBodyModel") {
    stateMachine.test(props()) {
      // load purchase amounts
      awaitLoader()

      awaitSheet<SelectPurchaseAmountBodyModel> {
        toolbar?.middleAccessory?.title.shouldBe("Choose an amount")
        // tap back button
        onBack.shouldNotBeNull().invoke()
      }

      // Verify onExit was called
      onExitCalls.awaitItem()
    }
  }
})

private suspend fun StateMachineTester<PartnershipsPurchaseAmountUiProps, SheetModel>.awaitLoader() {
  awaitSheet<FormBodyModel> {
    mainContentList[0].shouldBeTypeOf<Loader>()
  }
}

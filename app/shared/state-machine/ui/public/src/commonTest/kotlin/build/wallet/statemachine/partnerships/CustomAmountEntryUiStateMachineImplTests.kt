package build.wallet.statemachine.partnerships

import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CustomAmountEntryUiStateMachineImplTests : FunSpec({
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val defaultMoneyCalculatorModel =
    MoneyCalculatorModel(
      primaryAmount = FiatMoney.usd(10.0),
      secondaryAmount = BitcoinMoney.sats(1000),
      amountModel =
        MoneyAmountEntryModel(
          primaryAmount = "$10",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "1000 sats"
        ),
      keypadModel = KeypadModel(showDecimal = true, onButtonPress = {})
    )
  val moneyCalculatorUiStateMachine =
    object : MoneyCalculatorUiStateMachine, StateMachineMock<MoneyCalculatorUiProps, MoneyCalculatorModel>(
      defaultMoneyCalculatorModel
    ) {}

  val stateMachine =
    CustomAmountEntryUiStateMachineImpl(moneyCalculatorUiStateMachine, moneyDisplayFormatter)

  fun props(
    minimumAmount: FiatMoney = FiatMoney.usd(20.0),
    maximumAmount: FiatMoney = FiatMoney.usd(100.0),
    fiatCurrency: FiatCurrency = USD,
  ) = CustomAmountEntryUiProps(
    minimumAmount = minimumAmount,
    maximumAmount = maximumAmount,
    fiatCurrency = fiatCurrency,
    onNext = {},
    onBack = {}
  )

  test("custom amount entry not in range") {
    stateMachine.test(props()) {
      awaitScreenWithBody<CustomAmountBodyModel> {
        this.amountModel.shouldBe(defaultMoneyCalculatorModel.amountModel)
        this.toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("From $20.00 to $100.00")
        this.primaryButton.isEnabled.shouldBeFalse()
      }
    }
  }

  test("custom amount entry within range") {
    stateMachine.test(props(minimumAmount = FiatMoney.usd(10.0))) {
      awaitScreenWithBody<CustomAmountBodyModel> {
        this.primaryButton.isEnabled.shouldBeTrue()
      }
    }
  }
})

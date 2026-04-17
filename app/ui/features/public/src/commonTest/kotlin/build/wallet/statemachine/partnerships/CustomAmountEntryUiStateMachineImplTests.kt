package build.wallet.statemachine.partnerships

import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.components.label.LabelTreatment
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
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val moneyCalculatorUiStateMachine =
    object : MoneyCalculatorUiStateMachine,
      StateMachineMock<MoneyCalculatorUiProps, MoneyCalculatorModel>(
        defaultMoneyCalculatorModel
      ) {}

  val stateMachine = CustomAmountEntryUiStateMachineImpl(
    moneyCalculatorUiStateMachine,
    moneyDisplayFormatter,
    fiatCurrencyPreferenceRepository
  )

  fun props(
    minimumAmount: FiatMoney = FiatMoney.usd(20.0),
    maximumAmount: FiatMoney = FiatMoney.usd(100.0),
  ) = CustomAmountEntryUiProps(
    minimumAmount = minimumAmount,
    maximumAmount = maximumAmount,
    onNext = {},
    onBack = {}
  )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    moneyCalculatorUiStateMachine.reset()
  }

  test("custom amount entry not in range") {
    stateMachine.test(props()) {
      awaitBody<CustomAmountBodyModel> {
        this.toolbar.middleAccessory.shouldNotBeNull().subtitle.shouldBe("From $20.00 to $100.00")
        this.primaryButton.isEnabled.shouldBeFalse()

        this.amountModel.secondaryAmount.shouldBe("Minimum buy amount is $20.00")
        this.amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
      }
    }
  }

  test("custom amount entry within range") {
    stateMachine.test(props(minimumAmount = FiatMoney.usd(10.0))) {
      awaitBody<CustomAmountBodyModel> {
        this.primaryButton.isEnabled.shouldBeTrue()

      }
    }
  }

  test("custom amount entry at zero shows no error") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.zeroUsd(),
        secondaryAmount = BitcoinMoney.zero(),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          )
      )
    )

    stateMachine.test(props()) {
      awaitBody<CustomAmountBodyModel> {
        this.primaryButton.isEnabled.shouldBeFalse()

      }
    }
  }

  test("custom amount entry above maximum shows max error in context line") {
    moneyCalculatorUiStateMachine.emitModel(
      defaultMoneyCalculatorModel.copy(
        primaryAmount = FiatMoney.usd(150.0),
        secondaryAmount = BitcoinMoney.sats(15_000),
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$150",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "15,000 sats"
          )
      )
    )

    stateMachine.test(props()) {
      awaitBody<CustomAmountBodyModel> {
        this.primaryButton.isEnabled.shouldBeFalse()

        this.amountModel.secondaryAmount.shouldBe("Maximum buy amount is $100.00")
        this.amountContextLineTreatment.shouldBe(LabelTreatment.Destructive)
      }
    }
  }
})

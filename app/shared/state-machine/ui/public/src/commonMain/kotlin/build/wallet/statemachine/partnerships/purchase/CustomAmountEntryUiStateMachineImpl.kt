package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine

@BitkeyInject(ActivityScope::class)
class CustomAmountEntryUiStateMachineImpl(
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : CustomAmountEntryUiStateMachine {
  @Composable
  override fun model(props: CustomAmountEntryUiProps): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val calculatorModel =
      moneyCalculatorUiStateMachine.model(
        props =
          MoneyCalculatorUiProps(
            inputAmountCurrency = fiatCurrency,
            secondaryDisplayAmountCurrency = BTC,
            initialAmountInInputCurrency = FiatMoney.zero(fiatCurrency),
            exchangeRates = null
          )
      )

    val enteredMoney = calculatorModel.primaryAmount as FiatMoney
    val enteredAmountInRange =
      enteredMoney.value in props.minimumAmount.value..props.maximumAmount.value

    val bodyModel =
      CustomAmountBodyModel(
        onBack = props.onBack,
        limits =
          "From ${moneyDisplayFormatter.format(props.minimumAmount)} to " +
            moneyDisplayFormatter.format(props.maximumAmount),
        amountModel = calculatorModel.amountModel,
        keypadModel = calculatorModel.keypadModel,
        continueButtonEnabled = enteredAmountInRange,
        onNext = { props.onNext(calculatorModel.primaryAmount) }
      )

    return bodyModel.asModalFullScreen()
  }
}

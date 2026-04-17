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
import build.wallet.ui.components.label.LabelTreatment

@BitkeyInject(ActivityScope::class)
class CustomAmountEntryUiStateMachineImpl(
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : CustomAmountEntryUiStateMachine {
  @Composable
  override fun model(props: CustomAmountEntryUiProps): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val minimumAmountText = moneyDisplayFormatter.format(props.minimumAmount)
    val maximumAmountText = moneyDisplayFormatter.format(props.maximumAmount)

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
    val isAmountAboveMaximum = enteredMoney.value > props.maximumAmount.value
    val hasEnteredNonZeroAmount = !enteredMoney.isZero
    val contextLineError: String? = when {
      hasEnteredNonZeroAmount && isAmountAboveMaximum ->
        "Maximum buy amount is $maximumAmountText"
      hasEnteredNonZeroAmount && enteredMoney.value < props.minimumAmount.value ->
        "Minimum buy amount is $minimumAmountText"
      else -> null
    }

    val bodyModel = CustomAmountBodyModel(
      onBack = props.onBack,
      limits = "From $minimumAmountText to $maximumAmountText",
      amountModel = calculatorModel.amountModel.copy(
        secondaryAmount = contextLineError ?: calculatorModel.amountModel.secondaryAmount
      ),
      keypadModel = calculatorModel.keypadModel,
      isAmountAboveMaximum = isAmountAboveMaximum,
      amountContextLineTreatment = if (contextLineError != null) LabelTreatment.Destructive else LabelTreatment.Primary,
      continueButtonEnabled = enteredAmountInRange,
      onNext = { props.onNext(calculatorModel.primaryAmount) }
    )

    return bodyModel.asModalFullScreen()
  }
}

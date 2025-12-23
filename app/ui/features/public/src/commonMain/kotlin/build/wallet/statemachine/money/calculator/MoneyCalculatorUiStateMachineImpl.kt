package build.wallet.statemachine.money.calculator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.amount.Amount
import build.wallet.amount.Amount.DecimalNumber
import build.wallet.amount.Amount.WholeNumber
import build.wallet.amount.AmountCalculator
import build.wallet.amount.DecimalNumberCreator
import build.wallet.amount.DoubleFormatter
import build.wallet.amount.KeypadButton.Decimal
import build.wallet.amount.KeypadButton.Delete
import build.wallet.amount.KeypadButton.Digit
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.CryptoCurrency
import build.wallet.money.currency.Currency
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.statemachine.data.money.convertedOrZeroWithRates
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryProps
import build.wallet.statemachine.money.amount.MoneyAmountEntryUiStateMachine
import build.wallet.statemachine.money.calculator.AmountDenomination.FRACTIONAL_UNIT
import build.wallet.statemachine.money.calculator.AmountDenomination.UNIT
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger

@BitkeyInject(ActivityScope::class)
class MoneyCalculatorUiStateMachineImpl(
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val moneyAmountEntryUiStateMachine: MoneyAmountEntryUiStateMachine,
  private val amountCalculator: AmountCalculator,
  private val decimalNumberCreator: DecimalNumberCreator,
  private val doubleFormatter: DoubleFormatter,
) : MoneyCalculatorUiStateMachine {
  @Composable
  override fun model(props: MoneyCalculatorUiProps): MoneyCalculatorModel {
    var enteredAmount: Amount by remember(
      props.inputAmountCurrency,
      props.initialAmountInInputCurrency
    ) {
      val inputAmountDenomination = amountDenominationForCurrency(props.inputAmountCurrency)
      mutableStateOf(
        when (inputAmountDenomination) {
          UNIT -> {
            decimalNumberCreator.create(
              number = props.initialAmountInInputCurrency.value.doubleValue(exactRequired = false),
              maximumFractionDigits = props.initialAmountInInputCurrency.currency.fractionalDigits
            )
          }

          FRACTIONAL_UNIT -> {
            WholeNumber(
              number = props.initialAmountInInputCurrency.fractionalUnitValue.longValue()
            )
          }
        }
      )
    }

    val enteredMoney by remember(enteredAmount, props.inputAmountCurrency) {
      derivedStateOf {
        when (val amount = enteredAmount) {
          is DecimalNumber -> {
            val value = doubleFormatter.parse(amount.numberString)!!.toBigDecimal()
            when (props.inputAmountCurrency) {
              is FiatCurrency ->
                FiatMoney(
                  currency = props.inputAmountCurrency,
                  value = value
                )

              is CryptoCurrency -> BitcoinMoney.btc(value)
            }
          }

          is WholeNumber -> {
            // Only Bitcoin amounts can be entered in whole number amounts (satoshis) currently.
            require(props.inputAmountCurrency == BTC)
            BitcoinMoney.sats(amount.number.toBigInteger())
          }
        }
      }
    }

    val secondaryAmount =
      when (props.exchangeRates) {
        null -> null
        else ->
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = enteredMoney,
            toCurrency = props.secondaryDisplayAmountCurrency,
            rates = props.exchangeRates
          )
      }

    val amountModel =
      moneyAmountEntryUiStateMachine.model(
        props =
          MoneyAmountEntryProps(
            inputAmount = enteredAmount,
            secondaryAmount = secondaryAmount,
            inputAmountMoney = enteredMoney
          )
      )

    val showKeypadDecimal by remember(enteredAmount) {
      derivedStateOf { enteredAmount !is WholeNumber }
    }

    val keypadModel =
      remember(showKeypadDecimal, enteredAmount) {
        KeypadModel(
          showDecimal = showKeypadDecimal,
          onButtonPress = { keypadButton ->
            when (keypadButton) {
              Delete -> {
                enteredAmount =
                  amountCalculator.delete(enteredAmount)
              }

              is Digit -> {
                enteredAmount =
                  amountCalculator.add(
                    enteredAmount,
                    digit = keypadButton.value
                  )
              }

              is Decimal -> {
                enteredAmount = amountCalculator.decimal(enteredAmount)
              }

              else -> Unit
            }
          }
        )
      }

    return MoneyCalculatorModel(
      primaryAmount = enteredMoney,
      secondaryAmount = secondaryAmount,
      amountModel = amountModel,
      keypadModel = keypadModel
    )
  }

  private fun amountDenominationForCurrency(currency: Currency) =
    when (currency) {
      // We only support entering Fiat amounts as their full unit currently.
      is FiatCurrency -> UNIT

      // Use the [BitcoinDisplayUnit] to determine how we should enter Bitcoin amounts.
      is CryptoCurrency ->
        when (bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value) {
          BitcoinDisplayUnit.Bitcoin -> UNIT
          BitcoinDisplayUnit.Satoshi -> FRACTIONAL_UNIT
        }
    }
}

private enum class AmountDenomination {
  UNIT,
  FRACTIONAL_UNIT,
}

package build.wallet.statemachine.money.currency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiState.ShowingCurrencyFiatSelectionUiState
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiState.ShowingCurrencyPreferenceUiState
import build.wallet.ui.model.list.ListItemPickerMenu

class CurrencyPreferenceUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : CurrencyPreferenceUiStateMachine {
  @Composable
  override fun model(props: CurrencyPreferenceProps): ScreenModel {
    var state: CurrencyPreferenceUiState by remember {
      mutableStateOf(ShowingCurrencyPreferenceUiState)
    }

    return when (state) {
      is ShowingCurrencyPreferenceUiState ->
        CurrencyPreferenceFormModel(
          props = props,
          onFiatCurrencyPreferenceClick = { state = ShowingCurrencyFiatSelectionUiState }
        )

      is ShowingCurrencyFiatSelectionUiState ->
        FiatCurrencyListFormModel(
          onClose = { state = ShowingCurrencyPreferenceUiState },
          selectedCurrency = props.currencyPreferenceData.fiatCurrencyPreference,
          currencyList = fiatCurrencyRepository.allFiatCurrencies.value,
          onCurrencySelection = { selectedCurrency ->
            props.currencyPreferenceData.setFiatCurrencyPreference(selectedCurrency)
            // Once a selection is made, we auto-close the list screen.
            state = ShowingCurrencyPreferenceUiState
          }
        ).asModalScreen()
    }
  }

  @Composable
  private fun CurrencyPreferenceFormModel(
    props: CurrencyPreferenceProps,
    onFiatCurrencyPreferenceClick: () -> Unit,
  ): ScreenModel {
    val selectedCurrency = props.currencyPreferenceData.fiatCurrencyPreference
    val selectedBitcoinUnit = props.currencyPreferenceData.bitcoinDisplayUnitPreference

    // Primary amount: fiat
    val convertedFiatAmount: FiatMoney =
      remember(props.btcDisplayAmount) {
        currencyConverter
          .convert(
            fromAmount = props.btcDisplayAmount,
            toCurrency = selectedCurrency,
            atTime = null
          )
      }.collectAsState(null).value as? FiatMoney
        ?: FiatMoney.zero(selectedCurrency) // If we're unable to convert to the currency, show zero amount
    val moneyHomeHeroPrimaryAmountString = moneyDisplayFormatter.format(convertedFiatAmount)

    // Secondary amount: bitcoin
    val moneyHomeHeroSecondaryAmountString =
      remember(props.btcDisplayAmount, selectedBitcoinUnit) {
        moneyDisplayFormatter
          .format(props.btcDisplayAmount)
      }

    var isShowingBitcoinUnitPicker by remember { mutableStateOf(false) }

    val bitcoinDisplayPreferencePickerModel =
      remember(isShowingBitcoinUnitPicker, selectedBitcoinUnit) {
        ListItemPickerMenu(
          isShowing = isShowingBitcoinUnitPicker,
          selectedOption = selectedBitcoinUnit.displayText,
          options = BitcoinDisplayUnit.entries.map { it.displayText },
          onOptionSelected = { option ->
            val displayUnit = BitcoinDisplayUnit.entries.first { option == it.displayText }
            props.currencyPreferenceData.setBitcoinDisplayUnitPreference(displayUnit)
            isShowingBitcoinUnitPicker = false
          },
          onDismiss = {
            isShowingBitcoinUnitPicker = false
          }
        )
      }

    return CurrencyPreferenceFormModel(
      onBack = props.onBack,
      moneyHomeHero =
        FormMainContentModel.MoneyHomeHero(
          primaryAmount = moneyHomeHeroPrimaryAmountString,
          secondaryAmount = moneyHomeHeroSecondaryAmountString
        ),
      fiatCurrencyPreferenceString = selectedCurrency.textCode.code,
      onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
      bitcoinDisplayPreferenceString = selectedBitcoinUnit.displayText,
      bitcoinDisplayPreferencePickerModel = bitcoinDisplayPreferencePickerModel,
      onBitcoinDisplayPreferenceClick = {
        isShowingBitcoinUnitPicker = true
      },
      onDone = props.onDone
    ).asRootScreen()
  }
}

sealed interface CurrencyPreferenceUiState {
  data object ShowingCurrencyPreferenceUiState : CurrencyPreferenceUiState

  data object ShowingCurrencyFiatSelectionUiState : CurrencyPreferenceUiState
}

package build.wallet.statemachine.money.currency

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.HideBalancePreference
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrenciesService
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.pricechart.BitcoinPriceCardPreference
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiState.*
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceService
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class AppearancePreferenceUiStateMachineImpl(
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val eventTracker: EventTracker,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrenciesService: FiatCurrenciesService,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val hideBalancePreference: HideBalancePreference,
  private val bitcoinPriceCardPreference: BitcoinPriceCardPreference,
  private val bitcoinWalletService: BitcoinWalletService,
  private val themePreferenceService: ThemePreferenceService,
) : AppearancePreferenceUiStateMachine {
  @Composable
  override fun model(props: AppearancePreferenceProps): ScreenModel {
    var state: CurrencyPreferenceUiState by remember {
      mutableStateOf(ShowingCurrencyPreferenceUiState(isHideBalanceEnabled = false))
    }

    val selectedFiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val selectedThemePreference by themePreferenceService.themePreference().collectAsState(ThemePreference.System)

    val isHideBalanceEnabled by remember {
      hideBalancePreference.isEnabled
    }.onEach {
      when (val s = state) {
        is ShowingCurrencyPreferenceUiState -> state = s.copy(isHideBalanceEnabled = it)
        else -> {
          // no-op
        }
      }
    }
      .collectAsState(false)

    return when (val uiState = state) {
      is ShowingCurrencyPreferenceUiState -> {
        CurrencyPreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          isThemePreferenceEnabled = themePreferenceService.isThemePreferenceEnabled,
          themePreferenceString = selectedThemePreference.displayText,
          onThemePreferenceClick = {
            state = ShowingThemeSelectionUiState(
              isHideBalanceEnabled = isHideBalanceEnabled,
              selectedTheme = selectedThemePreference
            )
          },
          onFiatCurrencyPreferenceClick = { state = ShowingCurrencyFiatSelectionUiState }
        )
      }

      is ShowingCurrencyFiatSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        val onCurrencySelection: (FiatCurrency) -> Unit = remember(scope, isHideBalanceEnabled) {
          { selectedCurrency ->
            scope.launch {
              fiatCurrencyPreferenceRepository.setFiatCurrencyPreference(selectedCurrency)
                .onSuccess {
                  eventTracker.track(Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)
                }
              state = ShowingCurrencyPreferenceUiState(isHideBalanceEnabled)
            }
          }
        }
        FiatCurrencyListFormModel(
          onClose = { state = ShowingCurrencyPreferenceUiState(isHideBalanceEnabled) },
          selectedCurrency = selectedFiatCurrency,
          currencyList = fiatCurrenciesService.allFiatCurrencies.value,
          onCurrencySelection = onCurrencySelection
        ).asModalScreen()
      }

      is ShowingThemeSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        var selectedTheme by remember {
          mutableStateOf(uiState.selectedTheme ?: ThemePreference.System)
        }

        CurrencyPreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          isThemePreferenceEnabled = themePreferenceService.isThemePreferenceEnabled,
          themePreferenceString = selectedTheme.displayText,
          onThemePreferenceClick = {},
          onFiatCurrencyPreferenceClick = {},
          bottomSheetModel = themeSelectionSheetModel(
            selectedTheme = selectedTheme,
            onSelectTheme = { theme ->
              selectedTheme = theme
              scope.launch {
                themePreferenceService.setThemePreference(theme)
                  .onSuccess {
                    eventTracker.track(theme.analyticsAction)
                  }
              }
            },
            onExit = { state = ShowingCurrencyPreferenceUiState(uiState.isHideBalanceEnabled) }
          )
        )
      }
    }
  }

  @Composable
  private fun CurrencyPreferenceFormModel(
    props: AppearancePreferenceProps,
    selectedFiatCurrency: FiatCurrency,
    isHideBalanceEnabled: Boolean,
    isThemePreferenceEnabled: Boolean,
    themePreferenceString: String,
    onThemePreferenceClick: () -> Unit,
    onFiatCurrencyPreferenceClick: () -> Unit,
    bottomSheetModel: SheetModel? = null,
  ): ScreenModel {
    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState().value

    val btcDisplayAmount = when (transactionsData) {
      null -> BitcoinMoney.zero()
      else -> transactionsData.balance.total
    }

    val isBitcoinPriceCardEnabled by bitcoinPriceCardPreference.isEnabled.collectAsState()
    val selectedBitcoinUnit by bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.collectAsState()

    // Primary amount: fiat
    val convertedFiatAmount: FiatMoney =
      remember(btcDisplayAmount) {
        currencyConverter
          .convert(
            fromAmount = btcDisplayAmount,
            toCurrency = selectedFiatCurrency,
            atTime = null
          )
      }.collectAsState(null).value as? FiatMoney
        ?: FiatMoney.zero(selectedFiatCurrency) // If we're unable to convert to the currency, show zero amount
    val moneyHomeHeroPrimaryAmountString = moneyDisplayFormatter.format(convertedFiatAmount)

    // Secondary amount: bitcoin
    val moneyHomeHeroSecondaryAmountString =
      remember(btcDisplayAmount, selectedBitcoinUnit) {
        moneyDisplayFormatter
          .format(btcDisplayAmount)
      }

    var isShowingBitcoinUnitPicker by remember { mutableStateOf(false) }

    val scope = rememberStableCoroutineScope()
    val bitcoinDisplayPreferencePickerModel =
      remember(isShowingBitcoinUnitPicker, selectedBitcoinUnit) {
        ListItemPickerMenu(
          isShowing = isShowingBitcoinUnitPicker,
          selectedOption = selectedBitcoinUnit.displayText,
          options = BitcoinDisplayUnit.entries.map { it.displayText },
          onOptionSelected = { option ->
            scope.launch {
              val displayUnit = BitcoinDisplayUnit.entries
                .first { option == it.displayText }

              bitcoinDisplayPreferenceRepository
                .setBitcoinDisplayUnit(displayUnit)
                .onSuccess {
                  eventTracker.track(Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)
                }
              isShowingBitcoinUnitPicker = false
            }
          },
          onDismiss = {
            isShowingBitcoinUnitPicker = false
          }
        )
      }

    val onEnableHideBalanceChanged: (Boolean) -> Unit = remember(scope) {
      { isEnabled ->
        scope.launch { hideBalancePreference.set(isEnabled) }
      }
    }

    val onBitcoinPriceCardPreferenceClick: (Boolean) -> Unit = remember(scope) {
      { isEnabled ->
        scope.launch { bitcoinPriceCardPreference.set(isEnabled) }
      }
    }

    return AppearancePreferenceFormModel(
      onBack = props.onBack,
      moneyHomeHero =
        FormMainContentModel.MoneyHomeHero(
          primaryAmount = moneyHomeHeroPrimaryAmountString,
          secondaryAmount = moneyHomeHeroSecondaryAmountString,
          isHidden = isHideBalanceEnabled
        ),
      isThemePreferenceEnabled = isThemePreferenceEnabled,
      themePreferenceString = themePreferenceString,
      onThemePreferenceClick = onThemePreferenceClick,
      fiatCurrencyPreferenceString = selectedFiatCurrency.textCode.code,
      onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
      bitcoinDisplayPreferenceString = selectedBitcoinUnit.displayText,
      bitcoinDisplayPreferencePickerModel = bitcoinDisplayPreferencePickerModel,
      isHideBalanceEnabled = isHideBalanceEnabled,
      isBitcoinPriceCardEnabled = isBitcoinPriceCardEnabled,
      onEnableHideBalanceChanged = onEnableHideBalanceChanged,
      onBitcoinDisplayPreferenceClick = {
        isShowingBitcoinUnitPicker = true
      },
      onBitcoinPriceCardPreferenceClick = onBitcoinPriceCardPreferenceClick
    ).asRootScreen(
      bottomSheetModel = bottomSheetModel
    )
  }
}

sealed interface CurrencyPreferenceUiState {
  data class ShowingCurrencyPreferenceUiState(
    val isHideBalanceEnabled: Boolean = false,
  ) : CurrencyPreferenceUiState

  data object ShowingCurrencyFiatSelectionUiState : CurrencyPreferenceUiState

  data class ShowingThemeSelectionUiState(
    val isHideBalanceEnabled: Boolean = false,
    val selectedTheme: ThemePreference,
  ) : CurrencyPreferenceUiState
}

private val ThemePreference.analyticsAction: Action
  get() = when (this) {
    is ThemePreference.System -> Action.ACTION_APP_THEME_PREFERENCE_SYSTEM
    is ThemePreference.Manual -> when (value) {
      Theme.LIGHT -> Action.ACTION_APP_THEME_PREFERENCE_LIGHT
      Theme.DARK -> Action.ACTION_APP_THEME_PREFERENCE_DARK
    }
  }

private val ThemePreference.displayText: String
  get() = when (this) {
    is ThemePreference.System -> "System"
    is ThemePreference.Manual -> when (value) {
      Theme.LIGHT -> "Light"
      Theme.DARK -> "Dark"
    }
  }

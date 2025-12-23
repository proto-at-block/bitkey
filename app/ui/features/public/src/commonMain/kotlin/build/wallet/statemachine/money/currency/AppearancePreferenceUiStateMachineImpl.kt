package build.wallet.statemachine.money.currency

import androidx.compose.runtime.*
import bitkey.ui.framework.StringResourceProvider
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.inappsecurity.HideBalancePreference
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrenciesService
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.display.appearanceLabel
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.pricechart.BitcoinPriceCardPreference
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ChartRangePreference
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.AppearancePreferenceUiState.*
import build.wallet.statemachine.pricechart.TimeScaleListFormModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceService
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.map
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
  private val chartRangePreference: ChartRangePreference,
  private val stringResourceProvider: StringResourceProvider,
  private val bip177FeatureFlag: Bip177FeatureFlag,
) : AppearancePreferenceUiStateMachine {
  @Composable
  override fun model(props: AppearancePreferenceProps): ScreenModel {
    var state: AppearancePreferenceUiState by remember {
      mutableStateOf(
        ShowingPreferencesUiState(
          isHideBalanceEnabled = false,
          selectedSection = AppearanceSection.DISPLAY
        )
      )
    }

    val selectedFiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference
      .collectAsState()

    val selectedThemePreference by themePreferenceService.themePreference()
      .collectAsState(ThemePreference.System)

    val isHideBalanceEnabled by remember {
      hideBalancePreference.isEnabled
    }.onEach {
      when (val s = state) {
        is ShowingPreferencesUiState -> state = s.copy(isHideBalanceEnabled = it)
        else -> {
          // no-op
        }
      }
    }.collectAsState(false)

    return when (val uiState = state) {
      is ShowingPreferencesUiState -> {
        AppearancePreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          selectedSection = uiState.selectedSection,
          onSectionSelected = { newSection ->
            state = uiState.copy(selectedSection = newSection)
          },
          themePreferenceString = selectedThemePreference.displayText,
          onThemePreferenceClick = {
            state = ShowingThemeSelectionUiState(
              isHideBalanceEnabled = isHideBalanceEnabled,
              selectedTheme = selectedThemePreference
            )
          },
          onFiatCurrencyPreferenceClick = { state = ShowingFiatSelectionUiState },
          onDefaultTimeScalePreferenceClick = { state = ShowingDefaultTimeScaleSelectionUiState },
          onBitcoinDisplayPreferenceClick = {
            state = ShowingBitcoinDisplayUnitSelectionUiState(
              isHideBalanceEnabled = isHideBalanceEnabled,
              selectedUnit = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
            )
          }
        )
      }

      is ShowingFiatSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        val onCurrencySelection: (FiatCurrency) -> Unit = remember(scope, isHideBalanceEnabled) {
          { selectedCurrency ->
            scope.launch {
              fiatCurrencyPreferenceRepository.setFiatCurrencyPreference(selectedCurrency)
                .onSuccess {
                  eventTracker.track(Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)
                }
              state = ShowingPreferencesUiState(
                isHideBalanceEnabled = isHideBalanceEnabled,
                selectedSection = AppearanceSection.CURRENCY
              )
            }
          }
        }
        FiatCurrencyListFormModel(
          onClose = {
            state = ShowingPreferencesUiState(
              isHideBalanceEnabled = isHideBalanceEnabled,
              selectedSection = AppearanceSection.CURRENCY
            )
          },
          selectedCurrency = selectedFiatCurrency,
          currencyList = fiatCurrenciesService.allFiatCurrencies.value,
          onCurrencySelection = onCurrencySelection
        ).asModalScreen()
      }

      is ShowingBitcoinDisplayUnitSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        var selectedUnit by remember { mutableStateOf(uiState.selectedUnit) }
        val isBip177Enabled by remember {
          bip177FeatureFlag.flagValue().map { it.isEnabled() }
        }.collectAsState(initial = bip177FeatureFlag.isEnabled())

        // Get the current balance
        val transactionsData by remember {
          bitcoinWalletService.transactionsData()
        }.collectAsState()

        val balance = transactionsData?.balance?.total ?: BitcoinMoney.zero()

        AppearancePreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          selectedSection = AppearanceSection.CURRENCY,
          themePreferenceString = selectedThemePreference.displayText,
          onThemePreferenceClick = {},
          onFiatCurrencyPreferenceClick = {},
          bottomSheetModel = bitcoinDisplayUnitSelectionSheetModel(
            selectedUnit = selectedUnit,
            balance = balance,
            isBip177Enabled = isBip177Enabled,
            moneyDisplayFormatter = moneyDisplayFormatter,
            onSelectUnit = { unit ->
              selectedUnit = unit
              scope.launch {
                bitcoinDisplayPreferenceRepository
                  .setBitcoinDisplayUnit(unit)
                  .onSuccess {
                    eventTracker.track(Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)
                  }
                state = ShowingPreferencesUiState(
                  isHideBalanceEnabled = uiState.isHideBalanceEnabled,
                  selectedSection = AppearanceSection.CURRENCY
                )
              }
            },
            onExit = {
              state = ShowingPreferencesUiState(
                isHideBalanceEnabled = uiState.isHideBalanceEnabled,
                selectedSection = AppearanceSection.CURRENCY
              )
            }
          ),
          onDefaultTimeScalePreferenceClick = {},
          onBitcoinDisplayPreferenceClick = {}
        )
      }

      is ShowingThemeSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        var selectedTheme by remember {
          mutableStateOf(uiState.selectedTheme)
        }

        AppearancePreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          selectedSection = AppearanceSection.DISPLAY,
          onSectionSelected = { },
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
            onExit = {
              state = ShowingPreferencesUiState(
                isHideBalanceEnabled = uiState.isHideBalanceEnabled,
                selectedSection = AppearanceSection.DISPLAY // Since theme is in Display section
              )
            }
          ),
          onDefaultTimeScalePreferenceClick = {},
          onBitcoinDisplayPreferenceClick = {}
        )
      }

      ShowingDefaultTimeScaleSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        val onTimeScaleSelection: (ChartRange) -> Unit = remember(scope) {
          { selectedTimeScale ->
            scope.launch {
              chartRangePreference.set(selectedTimeScale)
              state = ShowingPreferencesUiState(
                isHideBalanceEnabled = isHideBalanceEnabled,
                selectedSection = AppearanceSection.CURRENCY // Since time scale is in Currency section
              )
            }
          }
        }
        TimeScaleListFormModel(
          onClose = {
            state = ShowingPreferencesUiState(
              isHideBalanceEnabled = isHideBalanceEnabled,
              selectedSection = AppearanceSection.CURRENCY
            )
          },
          selectedTimeScale = chartRangePreference.selectedRange.value,
          labels = ChartRange.entries.map { stringResourceProvider.getString(it.diffLabel) },
          timeScales = ChartRange.entries - ChartRange.ALL,
          onTimeScaleSelection = onTimeScaleSelection
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun AppearancePreferenceFormModel(
    props: AppearancePreferenceProps,
    selectedFiatCurrency: FiatCurrency,
    isHideBalanceEnabled: Boolean,
    selectedSection: AppearanceSection,
    onSectionSelected: (AppearanceSection) -> Unit = {},
    themePreferenceString: String,
    onThemePreferenceClick: () -> Unit,
    onFiatCurrencyPreferenceClick: () -> Unit,
    bottomSheetModel: SheetModel? = null,
    onDefaultTimeScalePreferenceClick: () -> Unit,
    onBitcoinDisplayPreferenceClick: () -> Unit,
  ): ScreenModel {
    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState().value

    val btcDisplayAmount = when (transactionsData) {
      null -> BitcoinMoney.zero()
      else -> transactionsData.balance.total
    }

    val isBitcoinPriceCardEnabled by bitcoinPriceCardPreference.isEnabled.collectAsState()
    val selectedBitcoinUnit by bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.collectAsState()
    val chartTimeScalePreference by chartRangePreference.selectedRange.collectAsState()
    val scope = rememberStableCoroutineScope()
    val isBip177Enabled by remember {
      bip177FeatureFlag.flagValue().map { it.isEnabled() }
    }.collectAsState(initial = bip177FeatureFlag.isEnabled())

    // Primary amount: fiat
    val convertedFiatAmount by remember(btcDisplayAmount) {
      currencyConverter.convert(
        fromAmount = btcDisplayAmount,
        toCurrency = selectedFiatCurrency,
        atTime = null
      ).map {
        it as? FiatMoney ?: FiatMoney.zero(selectedFiatCurrency)
      }
    }.collectAsState(FiatMoney.zero(selectedFiatCurrency))

    val moneyHomeHeroPrimaryAmountString = moneyDisplayFormatter.format(convertedFiatAmount)

    // Secondary amount: bitcoin
    val moneyHomeHeroSecondaryAmountString =
      remember(btcDisplayAmount, selectedBitcoinUnit, isBip177Enabled) {
        moneyDisplayFormatter
          .format(btcDisplayAmount)
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

    return ScreenModel(
      body = AppearancePreferenceBodyModel(
        onBack = props.onBack,
        moneyHomeHero =
          FormMainContentModel.MoneyHomeHero(
            primaryAmount = moneyHomeHeroPrimaryAmountString,
            secondaryAmount = moneyHomeHeroSecondaryAmountString,
            isHidden = isHideBalanceEnabled
          ),
        selectedSection = selectedSection,
        onSectionSelected = onSectionSelected,
        themePreferenceString = themePreferenceString,
        onThemePreferenceClick = onThemePreferenceClick,
        fiatCurrencyPreferenceString = selectedFiatCurrency.textCode.code,
        onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
        bitcoinDisplayPreferenceString = selectedBitcoinUnit.appearanceLabel(isBip177Enabled),
        defaultTimeScalePreferenceString = stringResourceProvider.getString(chartTimeScalePreference.label),
        onDefaultTimeScalePreferenceClick = onDefaultTimeScalePreferenceClick,
        isHideBalanceEnabled = isHideBalanceEnabled,
        isBitcoinPriceCardEnabled = isBitcoinPriceCardEnabled,
        onEnableHideBalanceChanged = onEnableHideBalanceChanged,
        onBitcoinDisplayPreferenceClick = onBitcoinDisplayPreferenceClick,
        onBitcoinPriceCardPreferenceClick = onBitcoinPriceCardPreferenceClick
      ),
      bottomSheetModel = bottomSheetModel
    )
  }
}

sealed interface AppearancePreferenceUiState {
  data class ShowingPreferencesUiState(
    val isHideBalanceEnabled: Boolean = false,
    val selectedSection: AppearanceSection,
  ) : AppearancePreferenceUiState

  data object ShowingFiatSelectionUiState : AppearancePreferenceUiState

  data class ShowingThemeSelectionUiState(
    val isHideBalanceEnabled: Boolean = false,
    val selectedTheme: ThemePreference,
  ) : AppearancePreferenceUiState

  data object ShowingDefaultTimeScaleSelectionUiState : AppearancePreferenceUiState

  data class ShowingBitcoinDisplayUnitSelectionUiState(
    val isHideBalanceEnabled: Boolean,
    val selectedUnit: BitcoinDisplayUnit,
  ) : AppearancePreferenceUiState
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

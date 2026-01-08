package build.wallet.statemachine.money.currency

import androidx.compose.runtime.Composable
import app.cash.turbine.plusAssign
import bitkey.ui.framework.StringResourceProvider
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE
import build.wallet.analytics.v1.Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.inappsecurity.HideBalancePreferenceFake
import build.wallet.money.currency.FiatCurrenciesServiceFake
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.pricechart.BitcoinPriceCardPreferenceFake
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ChartRangePreferenceFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.pricechart.TimeScaleListFormModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.ui.theme.ThemePreferenceServiceFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.compose.resources.StringResource

class AppearancePreferenceUiStateMachineImplTests : FunSpec({
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val currencyConverter = CurrencyConverterFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val fiatCurrenciesService = FiatCurrenciesServiceFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val hideBalancePreference = HideBalancePreferenceFake()
  val bitcoinPriceCardPreference = BitcoinPriceCardPreferenceFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val themePreferenceService = ThemePreferenceServiceFake()
  val chartTimeScalePreference = ChartRangePreferenceFake()
  val bip177FeatureFlag = Bip177FeatureFlag(FeatureFlagDaoFake())
  val stringResourceProvider = object : StringResourceProvider {
    @Composable
    override fun getString(resourceId: StringResource): String {
      return resourceId.key
    }
  }
  val stateMachine = AppearancePreferenceUiStateMachineImpl(
    bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    eventTracker = eventTracker,
    currencyConverter = currencyConverter,
    fiatCurrenciesService = fiatCurrenciesService,
    moneyDisplayFormatter = moneyDisplayFormatter,
    hideBalancePreference = hideBalancePreference,
    bitcoinPriceCardPreference = bitcoinPriceCardPreference,
    bitcoinWalletService = bitcoinWalletService,
    themePreferenceService = themePreferenceService,
    chartRangePreference = chartTimeScalePreference,
    stringResourceProvider = stringResourceProvider,
    bip177FeatureFlag = bip177FeatureFlag
  )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val props = AppearancePreferenceProps(onBack = { onBackCalls += Unit })

  beforeTest {
    bitcoinDisplayPreferenceRepository.clear()
    fiatCurrencyPreferenceRepository.clear()
    currencyConverter.reset()
    fiatCurrenciesService.reset()
    hideBalancePreference.reset()
    bitcoinPriceCardPreference.clear()
    bitcoinWalletService.reset()
  }

  test("update fiat currency preference") {
    fiatCurrenciesService.allFiatCurrencies.value = listOf(USD, GBP)

    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      awaitBody<AppearancePreferenceBodyModel> {
        onSectionSelected(AppearanceSection.CURRENCY)
      }

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
        moneyHomeHero.isHidden.shouldBeFalse()
        moneyHomeHero.primaryAmount.shouldBe("$0.00")
        moneyHomeHero.secondaryAmount.shouldBe("0 sats")
        fiatCurrencyPreferenceString.shouldBe("USD")
        onFiatCurrencyPreferenceClick()
      }

      awaitBody<FiatCurrencyListFormModel> {
        selectedCurrency.shouldBe(USD)
        currencyList.shouldContainExactly(USD, GBP)
        onCurrencySelection(GBP)
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)

      awaitUntilBody<AppearancePreferenceBodyModel>(
        matching = {
          // Wait for the fiat currency preference to be updated, which might or might not happen
          // in the next model.
          it.fiatCurrencyPreferenceString == "GBP"
        }
      ) {
        moneyHomeHero.isHidden.shouldBeFalse()
        moneyHomeHero.primaryAmount.shouldBe("£0.00")
        moneyHomeHero.secondaryAmount.shouldBe("0 sats")
        fiatCurrencyPreferenceString.shouldBe("GBP")
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
      }
    }
  }

  test("update chart history preference") {
    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      awaitBody<AppearancePreferenceBodyModel> {
        onDefaultTimeScalePreferenceClick()
      }

      awaitBody<TimeScaleListFormModel> {
        onTimeScaleSelection(ChartRange.WEEK)
      }

      awaitBody<AppearancePreferenceBodyModel> {
        defaultTimeScalePreferenceString.shouldBe("chart_history_label_week")
      }

      chartTimeScalePreference.get().value.shouldBe(ChartRange.WEEK)
    }
  }

  test("theme selection sheet closes when onBack is called") {
    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      // Open theme selection
      awaitBody<AppearancePreferenceBodyModel> {
        onThemePreferenceClick()
      }

      awaitUntilSheet<ThemeSelectionBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      // Verify we're back to main appearance preference screen without bottom sheet
      awaitItem().bottomSheetModel.shouldBe(null)
    }
  }

  test("dismissing fiat currency sheet preserves selected section") {
    fiatCurrenciesService.allFiatCurrencies.value = listOf(USD, GBP)

    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.DISPLAY)
        onSectionSelected(AppearanceSection.CURRENCY)
      }

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
        onFiatCurrencyPreferenceClick()
      }

      awaitBody<FiatCurrencyListFormModel> {
        onClose()
      }

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
      }
    }
  }

  test("dismissing bitcoin display unit sheet preserves selected section") {
    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.DISPLAY)
        onSectionSelected(AppearanceSection.CURRENCY)
      }

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
        onBitcoinDisplayPreferenceClick()
      }

      awaitItem().also { screenModel ->
        screenModel.bottomSheetModel.shouldNotBeNull().onClosed()
      }

      awaitUntilBody<AppearancePreferenceBodyModel>(
        matching = { it.selectedSection == AppearanceSection.CURRENCY }
      ) {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
      }
    }
  }

  test("selecting bitcoin display unit closes sheet and updates preference") {
    stateMachine.test(props) {
      // loading theme
      awaitBody<AppearancePreferenceBodyModel>()

      awaitBody<AppearancePreferenceBodyModel> {
        onSectionSelected(AppearanceSection.CURRENCY)
      }

      awaitBody<AppearancePreferenceBodyModel> {
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
        onBitcoinDisplayPreferenceClick()
      }

      awaitUntilSheet<BitcoinDisplayUnitSelectionBodyModel> {
        selectedUnit.shouldBe(BitcoinDisplayUnit.Satoshi)
        onSelectUnit(BitcoinDisplayUnit.Bitcoin)
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)

      awaitUntilBody<AppearancePreferenceBodyModel>(
        matching = { it.bitcoinDisplayPreferenceString == "BTC" }
      ) {
        bitcoinDisplayPreferenceString.shouldBe("BTC")
        selectedSection.shouldBe(AppearanceSection.CURRENCY)
      }
    }
  }
})

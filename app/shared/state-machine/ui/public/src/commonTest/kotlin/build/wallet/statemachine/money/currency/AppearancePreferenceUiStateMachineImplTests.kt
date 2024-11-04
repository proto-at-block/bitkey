package build.wallet.statemachine.money.currency

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE
import build.wallet.bitcoin.transactions.TransactionsServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.inappsecurity.HideBalancePreferenceFake
import build.wallet.money.currency.FiatCurrenciesServiceFake
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.pricechart.BitcoinPriceCardPreferenceFake
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AppearancePreferenceUiStateMachineImplTests : FunSpec({
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val currencyConverter = CurrencyConverterFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val fiatCurrenciesService = FiatCurrenciesServiceFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val hideBalancePreference = HideBalancePreferenceFake()
  val bitcoinPriceCardPreference = BitcoinPriceCardPreferenceFake()
  val transactionsService = TransactionsServiceFake()
  val stateMachine = AppearancePreferenceUiStateMachineImpl(
    bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    eventTracker = eventTracker,
    currencyConverter = currencyConverter,
    fiatCurrenciesService = fiatCurrenciesService,
    moneyDisplayFormatter = moneyDisplayFormatter,
    hideBalancePreference = hideBalancePreference,
    bitcoinPriceCardPreference = bitcoinPriceCardPreference,
    transactionsService = transactionsService
  )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val props = AppearancePreferenceProps(onBack = { onBackCalls += Unit })

  beforeTest {
    bitcoinDisplayPreferenceRepository.clear()
    fiatCurrencyPreferenceRepository.clear()
    currencyConverter.reset()
    fiatCurrenciesService.reset()
    hideBalancePreference.clear()
    bitcoinPriceCardPreference.clear()
    transactionsService.reset()
  }

  test("update fiat currency preference") {
    fiatCurrenciesService.allFiatCurrencies.value = listOf(USD, GBP)

    stateMachine.test(props) {
      awaitScreenWithBody<AppearancePreferenceFormModel> {
        moneyHomeHero.isHidden.shouldBeFalse()
        moneyHomeHero.primaryAmount.shouldBe("$0.00")
        moneyHomeHero.secondaryAmount.shouldBe("0 sats")
        fiatCurrencyPreferenceString.shouldBe("USD")
        onFiatCurrencyPreferenceClick()
      }

      awaitScreenWithBody<FiatCurrencyListFormModel> {
        selectedCurrency.shouldBe(USD)
        currencyList.shouldContainExactly(USD, GBP)
        onCurrencySelection(GBP)
      }

      eventTracker.eventCalls.awaitItem().action.shouldBe(ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)

      awaitUntilScreenWithBody<AppearancePreferenceFormModel>(
        expectedBodyContentMatch = {
          // Wait for the fiat currency preference to be updated, which might or might not happen
          // in the next model.
          it.fiatCurrencyPreferenceString == "GBP"
        }
      ) {
        moneyHomeHero.isHidden.shouldBeFalse()
        moneyHomeHero.primaryAmount.shouldBe("£0.00")
        moneyHomeHero.secondaryAmount.shouldBe("0 sats")
        fiatCurrencyPreferenceString.shouldBe("GBP")
      }
    }
  }
})

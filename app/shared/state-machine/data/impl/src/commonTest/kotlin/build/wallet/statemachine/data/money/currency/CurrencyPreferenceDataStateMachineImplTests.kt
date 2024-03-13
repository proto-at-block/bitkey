package build.wallet.statemachine.data.money.currency

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CurrencyPreferenceDataStateMachineImplTests : FunSpec({

  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine =
    CurrencyPreferenceDataStateMachineImpl(
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      eventTracker = eventTracker,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )

  val props = Unit

  beforeTest {
    bitcoinDisplayPreferenceRepository.reset()
    fiatCurrencyPreferenceRepository.reset()
  }

  test("bitcoin display uses value from repository") {
    stateMachine.test(props) {
      awaitItem().bitcoinDisplayUnitPreference.shouldBe(BitcoinDisplayUnit.Satoshi)
      bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(
        BitcoinDisplayUnit.Bitcoin
      )
      awaitItem().bitcoinDisplayUnitPreference.shouldBe(BitcoinDisplayUnit.Bitcoin)
    }
  }

  test("set bitcoin display calls dao and emits event") {
    stateMachine.test(props) {
      awaitItem().apply {
        setBitcoinDisplayUnitPreference(BitcoinDisplayUnit.Bitcoin)
      }
      bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnitCalls?.awaitItem()
        .shouldBe(BitcoinDisplayUnit.Bitcoin)
      awaitItem().let { it.bitcoinDisplayUnitPreference.shouldBe(BitcoinDisplayUnit.Bitcoin) }
      eventTracker.eventCalls.awaitItem().action
        .shouldBe(Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)
    }
  }

  test("fiat currency uses pref value over default from repository") {
    stateMachine.test(props) {
      awaitItem().fiatCurrencyPreference.shouldBe(USD)
      fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.emit(GBP)
      fiatCurrencyPreferenceRepository.internalDefaultFiatCurrency.emit(EUR)
      awaitItem().fiatCurrencyPreference.shouldBe(GBP)
    }
  }

  test("fiat currency uses default value from repository when no pref") {
    stateMachine.test(props) {
      awaitItem().fiatCurrencyPreference.shouldBe(USD)
      fiatCurrencyPreferenceRepository.internalDefaultFiatCurrency.emit(GBP)
      awaitItem().fiatCurrencyPreference.shouldBe(GBP)
    }
  }

  test("set fiat currency calls repository") {
    stateMachine.test(props) {
      awaitItem().apply {
        setFiatCurrencyPreference(EUR)
      }
      fiatCurrencyPreferenceRepository.setFiatCurrencyPreferenceCalls.awaitItem()
        .shouldBe(EUR)
      eventTracker.eventCalls.awaitItem().action
        .shouldBe(Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)
    }
  }
})

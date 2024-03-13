package build.wallet.statemachine.money.currency

import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.Currency
import build.wallet.money.currency.FiatCurrencyRepositoryMock
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CurrencyPreferenceUiStateMachineImplTests : FunSpec({
  val fiatCurrencyRepository = FiatCurrencyRepositoryMock(turbines::create)
  val stateMachine =
    CurrencyPreferenceUiStateMachineImpl(
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      currencyConverter = CurrencyConverterFake(),
      fiatCurrencyRepository = fiatCurrencyRepository
    )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnDoneCalls = turbines.create<Unit>("props onDone calls")

  val setBitcoinDisplayUnitPreferenceCalls =
    turbines.create<BitcoinDisplayUnit>(
      "setBitcoinDisplayUnitPreference calls"
    )
  val setFiatCurrencyPreferenceDataCalls =
    turbines.create<Currency>(
      "setFiatCurrencyPreference calls"
    )

  val props =
    CurrencyPreferenceProps(
      onBack = { propsOnBackCalls.add(Unit) },
      btcDisplayAmount = BitcoinMoney.btc(1.0),
      currencyPreferenceData =
        CurrencyPreferenceData(
          bitcoinDisplayUnitPreference = BitcoinDisplayUnit.Satoshi,
          setBitcoinDisplayUnitPreference = { setBitcoinDisplayUnitPreferenceCalls.add(it) },
          fiatCurrencyPreference = USD,
          setFiatCurrencyPreference = { setFiatCurrencyPreferenceDataCalls.add(it) }
        ),
      onDone = { propsOnDoneCalls.add(Unit) }
    )

  test("onBack calls props") {
    stateMachine.test(props) {
      // Before currency conversion
      awaitScreenWithBody<FormBodyModel>()

      // After currency conversion
      awaitScreenWithBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      propsOnBackCalls.awaitItem()
    }
  }

  test("onDone calls props") {
    stateMachine.test(props) {
      // Before currency conversion
      awaitScreenWithBody<FormBodyModel>()

      // After currency conversion
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }

      propsOnDoneCalls.awaitItem()
    }
  }

  test("null onDone hides primary button") {
    stateMachine.test(props.copy(onDone = null)) {
      // Before currency conversion
      awaitScreenWithBody<FormBodyModel>()

      // After currency conversion
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldBeNull()
      }
    }
  }

  test("tap fiat row shows list, list selection calls fiatCurrencyPreference and closes sheet") {
    stateMachine.test(props) {
      fiatCurrencyRepository.allFiatCurrenciesFlow.emit(listOf(USD))

      // Preference screen (first is pre currency conversion)
      awaitScreenWithBody<FormBodyModel>()
      awaitScreenWithBody<FormBodyModel> {
        expectPreferenceScreen()
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          with(listGroupModel.items[0]) {
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      // List screen
      awaitScreenWithBody<FormBodyModel> {
        expectListScreen()
        val currencyList = mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()
        currencyList.listGroupModel.items.first().onClick.shouldNotBeNull().invoke()
        val selectedCurrency = setFiatCurrencyPreferenceDataCalls.awaitItem()
        selectedCurrency.shouldBe(USD)
      }

      // Back to Preference screen (first is pre currency conversion)
      awaitScreenWithBody<FormBodyModel>()
      awaitScreenWithBody<FormBodyModel> {
        expectPreferenceScreen()
      }
    }
  }

  test("tap bitcoin row shows picker") {
    stateMachine.test(props) {
      // Preference screen (first is pre currency conversion)
      awaitScreenWithBody<FormBodyModel>()
      awaitScreenWithBody<FormBodyModel> {
        expectPreferenceScreen()
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          with(listGroupModel.items[1]) {
            pickerMenu.shouldNotBeNull().isShowing.shouldBeFalse()
            onClick.shouldNotBeNull().invoke()
          }
        }
      }
      awaitScreenWithBody<FormBodyModel> {
        expectPreferenceScreen()
        with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          with(listGroupModel.items[1]) {
            pickerMenu.shouldNotBeNull().isShowing.shouldBeTrue()
          }
        }
      }
    }
  }
})

private fun FormBodyModel.expectPreferenceScreen() {
  with(header.shouldNotBeNull()) {
    headline.shouldBe("Currency")
    sublineModel.shouldNotBeNull().string.shouldBe(
      "Choose how you want currencies to display throughout the app."
    )
  }

  mainContentList[0].shouldBeTypeOf<FormMainContentModel.MoneyHomeHero>()

  with(mainContentList[1].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
    with(listGroupModel.items[0]) {
      title.shouldBe("Fiat")
    }
    with(listGroupModel.items[1]) {
      title.shouldBe("Bitcoin")
    }
  }
}

private fun FormBodyModel.expectListScreen() {
  toolbar?.middleAccessory.shouldNotBeNull().title.shouldBe("Fiat")
  header.shouldBeNull()
  mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()
}

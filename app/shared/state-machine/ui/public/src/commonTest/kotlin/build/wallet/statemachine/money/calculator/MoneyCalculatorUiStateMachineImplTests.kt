package build.wallet.statemachine.money.calculator

import build.wallet.amount.*
import build.wallet.amount.AmountCalculatorImpl
import build.wallet.amount.DecimalNumberCalculatorImpl
import build.wallet.amount.DecimalNumberCreatorImpl
import build.wallet.amount.WholeNumberCalculatorImpl
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.limit.ONE_BTC_IN_SATOSHIS
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.platform.settings.LocaleProviderFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryProps
import build.wallet.statemachine.money.amount.MoneyAmountEntryUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class MoneyCalculatorUiStateMachineImplTests : FunSpec({
  val localeProvider = LocaleProviderFake()
  val defaultMoneyAmountEntryModel =
    MoneyAmountEntryModel(
      primaryAmount = "$1",
      primaryAmountGhostedSubstringRange = null,
      secondaryAmount = "1000 sats"
    )
  val moneyAmountEntryUiStateMachineMock =
    object : MoneyAmountEntryUiStateMachine,
      StateMachineMock<MoneyAmountEntryProps, MoneyAmountEntryModel>(
        defaultMoneyAmountEntryModel
      ) {}

  val doubleFormatter = DoubleFormatterImpl(localeProvider)
  val decimalNumberCreator = DecimalNumberCreatorImpl(localeProvider, doubleFormatter)
  val preferenceDisplayRepository = BitcoinDisplayPreferenceRepositoryMock()
  val stateMachine =
    MoneyCalculatorUiStateMachineImpl(
      bitcoinDisplayPreferenceRepository = preferenceDisplayRepository,
      currencyConverter = CurrencyConverterFake(),
      moneyAmountEntryUiStateMachine = moneyAmountEntryUiStateMachineMock,
      amountCalculator = AmountCalculatorImpl(
        decimalNumberCalculator = DecimalNumberCalculatorImpl(
          decimalNumberCreator,
          localeProvider,
          doubleFormatter
        ),
        wholeNumberCalculator = WholeNumberCalculatorImpl()
      ),
      decimalNumberCreator = decimalNumberCreator,
      doubleFormatter
    )

  context("fiat as input") {
    val props =
      MoneyCalculatorUiProps(
        inputAmountCurrency = USD,
        secondaryDisplayAmountCurrency = BTC,
        initialAmountInInputCurrency = FiatMoney.usd(1.0),
        exchangeRates = emptyImmutableList()
      )

    test("produces correct primary and secondary amounts") {
      stateMachine.test(props) {
        val model = awaitItem()
        model.primaryAmount.shouldBe(FiatMoney.usd(1.0))
        model.secondaryAmount.shouldBe(BitcoinMoney.btc(3.0))
      }
    }

    test("keypad should show decimal") {
      stateMachine.test(props) {
        val model = awaitItem()
        model.keypadModel.showDecimal.shouldBeTrue()
      }
    }
  }

  context("bitcoin as input") {
    val props =
      MoneyCalculatorUiProps(
        inputAmountCurrency = BTC,
        secondaryDisplayAmountCurrency = USD,
        initialAmountInInputCurrency = BitcoinMoney.btc(1.0),
        exchangeRates = emptyImmutableList()
      )

    context("BTC as display") {
      beforeTest {
        preferenceDisplayRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Bitcoin)
      }

      test("produces correct primary and secondary amounts") {
        stateMachine.test(props) {
          val model = awaitItem()
          model.primaryAmount.shouldBe(BitcoinMoney.btc(1.0))
          model.secondaryAmount.shouldBe(FiatMoney.usd(3.0))
        }
      }

      test("keypad should show decimal") {
        stateMachine.test(props) {
          val model = awaitItem()
          model.keypadModel.showDecimal.shouldBeTrue()
        }
      }
    }
    context("Satoshi as display") {
      beforeTest {
        preferenceDisplayRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)
      }

      test("produces correct primary and secondary amounts") {
        stateMachine.test(props) {
          val model = awaitItem()
          model.primaryAmount.shouldBe(BitcoinMoney.sats(ONE_BTC_IN_SATOSHIS))
          model.secondaryAmount.shouldBe(FiatMoney.usd(3.0))
        }
      }

      test("keypad should not show decimal") {
        stateMachine.test(props) {
          val model = awaitItem()
          model.keypadModel.showDecimal.shouldBeFalse()
        }
      }
    }
  }
})

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
  val doubleFormatter = DoubleFormatterImpl(localeProvider)
  val decimalNumberCreator = DecimalNumberCreatorImpl(localeProvider, doubleFormatter)
  val amountCalculator = AmountCalculatorImpl(
    decimalNumberCalculator = DecimalNumberCalculatorImpl(
      decimalNumberCreator,
      localeProvider,
      doubleFormatter
    ),
    wholeNumberCalculator = WholeNumberCalculatorImpl()
  )
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

  val preferenceDisplayRepository = BitcoinDisplayPreferenceRepositoryMock()
  val stateMachine =
    MoneyCalculatorUiStateMachineImpl(
      bitcoinDisplayPreferenceRepository = preferenceDisplayRepository,
      currencyConverter = CurrencyConverterFake(),
      moneyAmountEntryUiStateMachine = moneyAmountEntryUiStateMachineMock,
      amountCalculator = amountCalculator,
      decimalNumberCreator = decimalNumberCreator,
      doubleFormatter
    )

  test("delete feedback only rejects when delete would be a no-op") {
    decimalNumber("0").isRejectedAmountEntryButton(KeypadButton.Delete, amountCalculator)
      .shouldBeTrue()
    decimalNumber("0.").isRejectedAmountEntryButton(KeypadButton.Delete, amountCalculator)
      .shouldBeFalse()
    decimalNumber("0.0").isRejectedAmountEntryButton(KeypadButton.Delete, amountCalculator)
      .shouldBeFalse()
  }

  test("decimal feedback rejects only duplicate decimal taps") {
    decimalNumber("1").isRejectedAmountEntryButton(KeypadButton.Decimal, amountCalculator)
      .shouldBeFalse()
    decimalNumber("1.").isRejectedAmountEntryButton(KeypadButton.Decimal, amountCalculator)
      .shouldBeTrue()
    decimalNumber("1.23").isRejectedAmountEntryButton(KeypadButton.Decimal, amountCalculator)
      .shouldBeTrue()
  }

  test("hard cap feedback still allows valid decimal continuation") {
    decimalNumber("999999999").isRejectedAmountEntryButton(KeypadButton.Digit.Zero, amountCalculator)
      .shouldBeTrue()
    decimalNumber("999999999").isRejectedAmountEntryButton(KeypadButton.Decimal, amountCalculator)
      .shouldBeFalse()
    decimalNumber("999999999.").isRejectedAmountEntryButton(KeypadButton.Digit.Zero, amountCalculator)
      .shouldBeFalse()
    decimalNumber("999999999.").isRejectedAmountEntryButton(KeypadButton.Digit.One, amountCalculator)
      .shouldBeFalse()
  }

  test("digit feedback rejects taps ignored by fractional precision limits") {
    decimalNumber("1.2").isRejectedAmountEntryButton(KeypadButton.Digit.Three, amountCalculator)
      .shouldBeFalse()
    decimalNumber("1.23").isRejectedAmountEntryButton(KeypadButton.Digit.Four, amountCalculator)
      .shouldBeTrue()
  }

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

private fun decimalNumber(
  numberString: String,
  maximumFractionDigits: Int = 2,
) = Amount.DecimalNumber(
  numberString = numberString,
  maximumFractionDigits = maximumFractionDigits,
  decimalSeparator = '.'
)

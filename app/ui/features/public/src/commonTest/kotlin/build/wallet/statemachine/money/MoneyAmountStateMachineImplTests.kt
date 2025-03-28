package build.wallet.statemachine.money

import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyAmountStateMachineImplTests : FunSpec({
  val currencyConverter = CurrencyConverterFake()
  val stateMachine =
    MoneyAmountUiStateMachineImpl(
      currencyConverter = currencyConverter,
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  test("fiat as primary amount - initial model") {
    stateMachine.test(
      MoneyAmountUiProps(
        primaryMoney = FiatMoney.usd(1.0),
        secondaryAmountCurrency = BTC
      )
    ) {
      awaitItem().shouldBe(
        MoneyAmountModel(
          primaryAmount = "$1.00",
          secondaryAmount = "~~"
        )
      )
      awaitItem().shouldBe(
        MoneyAmountModel(
          primaryAmount = "$1.00",
          secondaryAmount = "300,000,000 sats"
        )
      )
    }
  }

  test("btc as primary amount - initial model") {
    stateMachine.test(
      props =
        MoneyAmountUiProps(
          primaryMoney = BitcoinMoney.btc(0.01),
          secondaryAmountCurrency = USD
        )
    ) {
      awaitItem().shouldBe(
        MoneyAmountModel(
          primaryAmount = "1,000,000 sats",
          secondaryAmount = "~~"
        )
      )
      awaitItem().shouldBe(
        MoneyAmountModel(
          primaryAmount = "1,000,000 sats",
          secondaryAmount = "$0.03"
        )
      )
    }
  }
})

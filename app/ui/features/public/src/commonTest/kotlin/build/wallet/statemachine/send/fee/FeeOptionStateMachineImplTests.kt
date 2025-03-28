package build.wallet.statemachine.send.fee

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.USD
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FeeOptionStateMachineImplTests : FunSpec({
  val stateMachine =
    FeeOptionUiStateMachineImpl(
      currencyConverter = CurrencyConverterFake(),
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  val onClickTurbine = turbines.create<Unit>("on click")

  val props =
    FeeOptionProps(
      feeAmount = BitcoinMoney.btc(1.0),
      selected = false,
      estimatedTransactionPriority = FASTEST,
      fiatCurrency = USD,
      onClick = {
        onClickTurbine += Unit
      },
      bitcoinBalance = BitcoinBalanceFake(confirmed = BitcoinMoney.btc(2.0)),
      transactionAmount = BitcoinMoney.btc(0.5),
      exchangeRates = immutableListOf()
    )

  test("State machine is initialized and currency is converted") {
    stateMachine.test(props) {
      with(awaitItem()) {
        optionName.shouldBe("Priority")
        transactionTime.shouldBe("~10 mins")
        transactionFee.shouldBe("$3.00 (100,000,000 sats)")
        selected.shouldBeFalse()
        enabled.shouldBeTrue()
        onClick.shouldNotBeNull().invoke()
      }

      onClickTurbine.awaitItem().shouldBe(Unit)
    }
  }

  test("onclick is null for an disabled option") {
    stateMachine.test(
      props.copy(feeAmount = BitcoinMoney.btc(3.0))
    ) {
      awaitItem().onClick.shouldBeNull()
    }
  }

  test("option is enabled when balance is exactly amount + fee") {
    stateMachine.test(props.copy(transactionAmount = BitcoinMoney.btc(1.0))) {
      with(awaitItem()) {
        enabled.shouldBeTrue()
      }
    }
  }

  test("option is disabled when balance is below amount + fee") {
    stateMachine.test(props.copy(transactionAmount = BitcoinMoney.btc(1.01))) {
      with(awaitItem()) {
        enabled.shouldBeFalse()
        infoText.shouldBe("Not enough balance")
      }
    }
  }

  test("option is not selected when it is not enabled") {
    stateMachine.test(
      props.copy(
        selected = true,
        transactionAmount = BitcoinMoney.btc(1.01)
      )
    ) {
      with(awaitItem()) {
        enabled.shouldBeFalse()
        selected.shouldBeFalse()
      }
    }
  }

  test("all fees equal text is shown when option is prop is true") {
    stateMachine.test(props.copy(showAllFeesEqualText = true)) {
      with(awaitItem()) {
        infoText.shouldBe("All network fees are the same—\nwe selected the fastest for you.")
      }
    }
  }
})

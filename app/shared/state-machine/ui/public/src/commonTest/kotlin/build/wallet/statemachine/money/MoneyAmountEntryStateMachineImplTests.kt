package build.wallet.statemachine.money

import build.wallet.amount.Amount.DecimalNumber
import build.wallet.amount.Amount.WholeNumber
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.money.input.MoneyInputFormatterMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryProps
import build.wallet.statemachine.money.amount.MoneyAmountEntryUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyAmountEntryStateMachineImplTests : FunSpec({
  val stateMachine =
    MoneyAmountEntryUiStateMachineImpl(
      moneyInputFormatter = MoneyInputFormatterMock(),
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )
  val props =
    MoneyAmountEntryProps(
      inputAmount =
        DecimalNumber(
          numberString = "1.00",
          maximumFractionDigits = 2,
          decimalSeparator = '.'
        ),
      secondaryAmount = BitcoinMoney.sats(300_000_000),
      inputAmountMoney = FiatMoney.usd(1.0)
    )

  test("fiat as primary amount - initial model") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBe(
        MoneyAmountEntryModel(
          primaryAmount = "MoneyInputFormatter.displayText",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "300,000,000 sats"
        )
      )
    }
  }

  test("btc as primary amount - initial model") {
    stateMachine.testWithVirtualTime(
      props =
        MoneyAmountEntryProps(
          inputAmount = WholeNumber(1000000),
          secondaryAmount = FiatMoney.usd(3),
          inputAmountMoney = BitcoinMoney.btc(0.01)
        )
    ) {
      awaitItem().shouldBe(
        MoneyAmountEntryModel(
          primaryAmount = "MoneyInputFormatter.displayText",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "$0.03"
        )
      )
    }
  }
})

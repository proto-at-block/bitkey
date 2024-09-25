package build.wallet.statemachine.transactions

import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransactionReceive
import build.wallet.bitcoin.transactions.BitcoinTransactionSend
import build.wallet.bitcoin.transactions.BitcoinTransactionUtxoConsolidation
import build.wallet.money.currency.USD
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.test
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import build.wallet.time.someInstant
import build.wallet.ui.model.list.ListItemSideTextTint.GREEN
import build.wallet.ui.model.list.ListItemSideTextTint.PRIMARY
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.toLocalDateTime

class TransactionItemUiStateMachineImplTests : FunSpec({
  val timeZoneProvider = TimeZoneProviderMock()
  val confirmedTime = BitcoinTransactionSend.confirmationTime()!!
  val broadcastTime = someInstant
  val timeToFormattedTime =
    mapOf(
      confirmedTime.toLocalDateTime(timeZoneProvider.current)
        to "confirmed-time",
      broadcastTime.toLocalDateTime(timeZoneProvider.current)
        to "broadcast-time"
    )

  val stateMachine =
    TransactionItemUiStateMachineImpl(
      currencyConverter =
        CurrencyConverterFake(
          conversionRate = 3.0,
          historicalConversionRate = mapOf(BlockTimeFake.timestamp to 4.0)
        ),
      dateTimeFormatter = DateTimeFormatterMock(timeToFormattedTime),
      timeZoneProvider = timeZoneProvider,
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  test("pending receive transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionReceive.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(GREEN)
      }

      awaitItem().let { // after currency conversion
        // Should use the current exchange rate
        it.sideText.shouldBe("+ \$3.00")
      }
    }
  }

  test("confirmed receive transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionReceive)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(GREEN)
      }

      awaitItem().let { // after currency conversion
        // Should use the historical exchange rate
        it.sideText.shouldBe("+ \$4.00")
      }
    }
  }

  test("pending send transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionSend.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("101,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().let { // after currency conversion
        // Should use the current exchange rate
        it.sideText.shouldBe("\$3.03")
      }
    }
  }

  test("confirmed send transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionSend)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("101,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().let { // after currency conversion
        // Should use the historical exchange rate
        it.sideText.shouldBe("\$4.04")
      }
    }
  }

  test("pending utxo consolidation transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionUtxoConsolidation.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().let { // after currency conversion
        // Should use the current exchange rate
        it.sideText.shouldBe("\$3.00")
      }
    }
  }

  test("confirmed utxo consolidation transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionUtxoConsolidation)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().let { // after currency conversion
        // Should use the historical exchange rate
        it.sideText.shouldBe("\$4.00")
      }
    }
  }
})

private fun makeProps(transaction: BitcoinTransaction) =
  TransactionItemUiProps(
    transaction = transaction,
    fiatCurrency = USD,
    onClick = {}
  )

package build.wallet.statemachine.transactions

import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.money.BitcoinMoney
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
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TransactionItemUiStateMachineImplTests : FunSpec({
  val timeZoneProvider = TimeZoneProviderMock()
  val confirmedTime = TEST_SEND_TXN.confirmationTime()!!
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
    stateMachine.test(makeProps(TEST_RECEIVE_TXN.copy(confirmationStatus = Pending))) {
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
    stateMachine.test(makeProps(TEST_RECEIVE_TXN)) {
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
    stateMachine.test(makeProps(TEST_SEND_TXN.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("broadcast-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,100 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().let { // after currency conversion
        // Should use the current exchange rate
        it.sideText.shouldBe("\$3.00")
      }
    }
  }

  test("confirmed send transaction model") {
    stateMachine.test(makeProps(TEST_SEND_TXN)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,100 sats")
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

private val TEST_ID = "c4f5835c0b77d438160cf54c4355208b0a39f58919ff4c221df6ebedc1ad67be"
private val TEST_RECEIVE_TXN =
  BitcoinTransaction(
    id = TEST_ID,
    broadcastTime = null,
    estimatedConfirmationTime = null,
    confirmationStatus =
      ConfirmationStatus.Confirmed(
        blockTime = BlockTimeFake
      ),
    recipientAddress = someBitcoinAddress,
    total = BitcoinMoney.btc(1.1),
    subtotal = BitcoinMoney.btc(1.0),
    fee = null,
    weight = null,
    vsize = null,
    incoming = true
  )
private val TEST_SEND_TXN =
  BitcoinTransaction(
    id = TEST_ID,
    broadcastTime = someInstant,
    estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
    confirmationStatus =
      ConfirmationStatus.Confirmed(
        blockTime = BlockTimeFake
      ),
    recipientAddress = someBitcoinAddress,
    total = BitcoinMoney.btc(1.00000100),
    subtotal = BitcoinMoney.btc(1.0),
    fee = BitcoinMoney.sats(100),
    weight = null,
    vsize = null,
    incoming = false
  )

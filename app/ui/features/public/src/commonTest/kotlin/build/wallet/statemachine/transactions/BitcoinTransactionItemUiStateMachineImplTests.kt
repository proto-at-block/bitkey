package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransactionReceive
import build.wallet.bitcoin.transactions.BitcoinTransactionSend
import build.wallet.bitcoin.transactions.BitcoinTransactionUtxoConsolidation
import build.wallet.money.currency.USD
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.test
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import build.wallet.time.someInstant
import build.wallet.ui.model.icon.BadgeType
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemSideTextTint.GREEN
import build.wallet.ui.model.list.ListItemSideTextTint.PRIMARY
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

class BitcoinTransactionItemUiStateMachineImplTests : FunSpec({
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
  val clock = ClockFake()

  val stateMachine =
    BitcoinTransactionItemUiStateMachineImpl(
      currencyConverter =
        CurrencyConverterFake(
          conversionRate = 3.0,
          historicalConversionRate = mapOf(BlockTimeFake.timestamp to 4.0, someInstant to 5.0)
        ),
      dateTimeFormatter = DateTimeFormatterMock(timeToFormattedTime),
      timeZoneProvider = timeZoneProvider,
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      clock = clock
    )

  beforeTest {
    clock.reset()
  }

  test("pending receive transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionReceive.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("")
        val label = it.titleLabel.shouldBeTypeOf<LabelModel.StringWithStyledSubstringModel>()
        label.string.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(GREEN)
        it.leadingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>()
          .model
          .badge
          .shouldBe(BadgeType.Loading)
      }

      awaitItem().sideText.shouldBe("+ $3.00")
    }
  }

  test("confirmed receive transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionReceive)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("")
        val label = it.titleLabel.shouldBeTypeOf<LabelModel.StringWithStyledSubstringModel>()
        label.string.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(GREEN)
      }

      awaitItem().sideText.shouldBe("+ $4.00")
    }
  }

  test("pending send transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionSend.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("")
        val label = it.titleLabel.shouldBeTypeOf<LabelModel.StringWithStyledSubstringModel>()
        label.string.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("101,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().sideText.shouldBe("$5.05")
    }
  }

  test("confirmed send transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionSend)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("")
        val label = it.titleLabel.shouldBeTypeOf<LabelModel.StringWithStyledSubstringModel>()
        label.string.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("101,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
        it.leadingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>()
          .model
          .badge
          .shouldBeNull()
      }

      awaitItem().sideText.shouldBe("$5.05")
    }
  }

  test("pending utxo consolidation transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionUtxoConsolidation.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("Consolidation")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().sideText.shouldBe("$3.00")
    }
  }

  test("confirmed utxo consolidation transaction model") {
    stateMachine.test(makeProps(BitcoinTransactionUtxoConsolidation)) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("Consolidation")
        it.secondaryText.shouldBe("confirmed-time")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("100,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
      }

      awaitItem().sideText.shouldBe("$4.00")
    }
  }

  test("late transaction model") {
    clock.advanceBy(15.minutes)

    stateMachine.test(makeProps(BitcoinTransactionSend.copy(confirmationStatus = Pending))) {
      awaitItem().let { // before currency conversion
        it.title.shouldBe("")
        val label = it.titleLabel.shouldBeTypeOf<LabelModel.StringWithStyledSubstringModel>()
        label.string.shouldBe("bc1z...xpcs")
        it.secondaryText.shouldBe("Pending")
        it.sideText.shouldBe("~~")
        it.secondarySideText.shouldBe("101,000,000 sats")
        it.sideTextTint.shouldBe(PRIMARY)
        it.leadingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>()
          .model
          .badge
          .shouldBe(BadgeType.Error)
      }

      awaitItem().sideText.shouldBe("$5.05")
    }
  }
})

private fun makeProps(transaction: BitcoinTransaction) =
  BitcoinTransactionItemUiProps(
    transaction = BitcoinWalletTransaction(transaction),
    fiatCurrency = USD,
    onClick = {}
  )

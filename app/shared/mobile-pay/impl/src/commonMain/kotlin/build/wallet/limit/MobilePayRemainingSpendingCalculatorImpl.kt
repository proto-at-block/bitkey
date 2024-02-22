package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class MobilePayRemainingSpendingCalculatorImpl internal constructor(
  // The clock used for obtaining the current time.
  private val clock: Clock,
  // When the limit resets in the limit's time zone.
  private val resetHour: Int = 3,
) : MobilePayRemainingSpendingCalculator {
  constructor() : this(Clock.System, 3)

  override fun remainingSpendingAmount(
    allTransactions: List<BitcoinTransaction>,
    limitAmountInBtc: BitcoinMoney,
    limitTimeZone: TimeZone,
  ): Money {
    val nowInstant = clock.now()
    val nowDateTime = nowInstant.toLocalDateTime(limitTimeZone)
    val lastResetInstant =
      LocalDateTime(
        // If it's past the reset hour today in the limit timezone, look back to today's reset hour
        // Otherwise, look back to yesterday's reset hour
        date =
          if (nowDateTime.hour >= resetHour) {
            nowDateTime.date
          } else {
            nowDateTime.date.minus(
              unit = DateTimeUnit.DAY
            )
          },
        time = LocalTime(hour = resetHour, minute = 0, second = 0)
      ).toInstant(limitTimeZone)

    // Sum up all send transactions that are either pending or were confirmed since the limit last reset.
    val transactionsBtcSubtotals =
      allTransactions
        .filter { !it.incoming } // Send transactions
        .filter {
          it.isPendingOrConfirmedInRange(lastResetInstant..nowInstant)
        } // Pending or since last reset
        .map { it.subtotal } // Get the total sent, not including fees

    // Add them all together
    val transactionsBtcTotal =
      transactionsBtcSubtotals.fold(
        BitcoinMoney.zero(),
        BitcoinMoney::plus
      )

    val diff = limitAmountInBtc - transactionsBtcTotal

    return maxOf(diff, BitcoinMoney.zero(), Money.Comparator)
  }
}

private fun BitcoinTransaction.isPendingOrConfirmedInRange(range: ClosedRange<Instant>): Boolean {
  return when (confirmationStatus) {
    is Pending -> true
    is Confirmed -> (confirmationStatus as Confirmed).blockTime.timestamp in range
  }
}

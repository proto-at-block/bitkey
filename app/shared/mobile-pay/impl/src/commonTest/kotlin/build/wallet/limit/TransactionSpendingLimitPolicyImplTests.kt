package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransactionMock
import build.wallet.money.BitcoinMoney
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

class TransactionSpendingLimitPolicyImplTests : FunSpec({

  val est = TimeZone.of("America/New_York")
  val pst = TimeZone.of("America/Los_Angeles")

  val today = LocalDate(year = 1985, monthNumber = 10, dayOfMonth = 26)
  val yesterday = today.minus(DateTimeUnit.DAY)

  val clock =
    ClockFake(
      now = LocalDateTime(date = today, time = LocalTime(hour = 9, minute = 0)).toInstant(pst)
    )
  val policy = MobilePayRemainingSpendingCalculatorImpl(clock)

  beforeEach {
    clock.now = LocalDateTime(date = today, time = LocalTime(hour = 9, minute = 0)).toInstant(pst)
  }

  test("Amount is not above limit with amount less than limit amount") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Receive txn. Should not contribute.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime = null,
            incoming = true
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(1_000))
  }

  test("Amount is not above limit with amount equal to limit amount") {
    policy.remainingSpendingAmount(
      allTransactions = listOf(),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(1_000))
  }

  test("Amount is above limit with amount greater than limit amount") {
    policy.remainingSpendingAmount(
      allTransactions = listOf(),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(1_000))
  }

  test("Amount is not above limit with pending transaction") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(100),
            confirmationTime = null
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(900))
  }

  test("Transaction fee does not count towards spending limit") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            fee = BitcoinMoney.sats(50),
            confirmationTime = null
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(100))
  }

  test("Received transaction does not count towards spending limit") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime = null
          ),
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(500),
            confirmationTime = null,
            incoming = true
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(50))
  }

  test("Amount is above limit with pending transaction") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime = null
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(50))
  }

  test("Amount is not above limit with confirmed transaction") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should contribute to calculation but not go over.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(100),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 8, minute = 0)
              ).toInstant(
                pst
              )
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(900))
  }

  test("Amount is not above limit with confirmed transaction large sent and received values") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Total sent is 2000 sats
          // Should contribute to calculation but not go over.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(2_000),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 8, minute = 0)
              ).toInstant(
                pst
              )
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(4_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(2_000))
  }

  test("Amount is above limit with confirmed transaction") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should contribute to calculation and go over.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 8, minute = 0)
              ).toInstant(
                pst
              )
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(900),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.zero())
  }

  test("Amount is above limit with confirmed transaction large sent and received values") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Total sent is 2000 sats
          // Should contribute to calculation and go over.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(2_000),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 8, minute = 0)
              ).toInstant(
                pst
              )
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_500),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.zero())
  }

  test("Amount is not above limit with confirmed transaction yesterday") {
    // Set now to 1AM, so transactions from yesterday contribute to the current limit window.
    clock.now =
      LocalDateTime(
        date = today,
        time = LocalTime(hour = 1, minute = 0)
      ).toInstant(pst)
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should contribute to calculation since it's past the reset hour (3AM) from the previous day.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(150),
            confirmationTime =
              LocalDateTime(
                date = yesterday,
                time = LocalTime(hour = 4, minute = 0)
              ).toInstant(pst)
          ),
          // Should not contribute to calculation since it's before the reset hour (3AM) from the previous day.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = yesterday,
                time = LocalTime(hour = 1, minute = 0)
              ).toInstant(pst)
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(850))
  }

  test("Amount is above limit with confirmed transaction yesterday") {
    // Set now to 1AM, so transactions from yesterday contribute to the current limit window.
    clock.now =
      LocalDateTime(
        date = today,
        time = LocalTime(hour = 1, minute = 0)
      ).toInstant(pst)
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should contribute to calculation since it's past the reset hour (3AM) from the previous day.
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = yesterday,
                time = LocalTime(hour = 4, minute = 0)
              ).toInstant(pst)
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(900),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.zero())
  }

  test("Amount is not above limit with confirmed transaction different time zones") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should not contribute to calculation (even though it's 4AM, because
          // it should be converted to the limit time zone in which it's 1AM)
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 4, minute = 0)
              ).toInstant(est)
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(1_000),
      limitTimeZone = pst
    ).shouldBe(BitcoinMoney.sats(1_000))
  }

  test("Amount is above limit with confirmed transaction different time zones") {
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should contribute to calculation (even though it's 1AM, because
          // it should be converted to the limit time zone in which it's 4AM)
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 1, minute = 0)
              ).toInstant(pst)
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(900),
      limitTimeZone = est
    ).shouldBe(BitcoinMoney.zero())
  }

  test("Amount is above limit now time zone different than limit time zone") {
    // Set now to 1AM PST.
    clock.now =
      LocalDateTime(
        date = today,
        time = LocalTime(hour = 1, minute = 0)
      ).toInstant(pst)
    policy.remainingSpendingAmount(
      allTransactions =
        listOf(
          // Should not contribute to calculation since it's before the reset hour (3AM) in the limit time zone (EST).
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(150),
            confirmationTime =
              LocalDateTime(
                date = yesterday,
                time = LocalTime(hour = 23, minute = 0)
              ).toInstant(pst)
          ),
          // Should contribute to calculation since it's after the reset hour (3AM) in the limit time zone (EST).
          BitcoinTransactionMock(
            total = BitcoinMoney.sats(950),
            confirmationTime =
              LocalDateTime(
                date = today,
                time = LocalTime(hour = 0, minute = 30)
              ).toInstant(pst)
          )
        ),
      limitAmountInBtc = BitcoinMoney.sats(900),
      limitTimeZone = est
    ).shouldBe(BitcoinMoney.zero())
  }
})

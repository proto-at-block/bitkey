package build.wallet.activity

import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransactionMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.*
import build.wallet.partnerships.PartnershipTransactionStatus.*
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class TransactionActivityMergerTests : FunSpec({
  val pendingPartnershipTxWithMatch = PartnershipTransaction(
    id = PartnershipTransactionId("pending-partnership-with-match"),
    partnerInfo = PartnerInfo(
      partnerId = PartnerId("test-partner"),
      name = "test-partner-name",
      logoUrl = "test-partner-logo-url",
      logoBadgedUrl = "test-partner-logo-badged-url"
    ),
    context = "test-context",
    type = PartnershipTransactionType.PURCHASE,
    status = PENDING,
    cryptoAmount = 1.23,
    txid = "pending-bitcoin-with-txid-match",
    fiatAmount = 3.21,
    fiatCurrency = IsoCurrencyTextCode("USD"),
    paymentMethod = "test-payment-method",
    created = Instant.fromEpochMilliseconds(248),
    updated = Instant.fromEpochMilliseconds(842),
    sellWalletAddress = "test-sell-wallet-address",
    partnerTransactionUrl = "https://fake-partner.com/transaction/test-id"
  )

  val pendingBitcoinTxWithMatch = BitcoinTransactionMock(
    txid = "pending-bitcoin-with-txid-match",
    total = BitcoinMoney.sats(1000),
    transactionType = Outgoing,
    confirmationTime = null
  )

  val confirmedPartnershipTxWithBackupMatch = pendingPartnershipTxWithMatch.copy(
    id = PartnershipTransactionId("confirmed-partnership-with-backup-match"),
    txid = "not-a-match",
    cryptoAmount = 0.00001,
    status = SUCCESS,
    type = PartnershipTransactionType.SALE
  )

  val confirmedBitcoinTxWithBackupMatch = BitcoinTransactionMock(
    txid = "confirmed-bitcoin-with-backup-match",
    total = BitcoinMoney.sats(1200),
    fee = BitcoinMoney.sats(200),
    transactionType = Outgoing,
    confirmationTime = someInstant
  )

  val pendingPartnershipTx = pendingPartnershipTxWithMatch.copy(
    id = PartnershipTransactionId("pending-partnership"),
    txid = "not-a-match"
  )

  val pendingBitcoinTx = BitcoinTransactionMock(
    txid = "pending-bitcoin",
    total = BitcoinMoney.sats(5000),
    transactionType = Outgoing,
    confirmationTime = null
  )

  val pendingPartnershipTxWithMultipleMatches = pendingPartnershipTxWithMatch.copy(
    id = PartnershipTransactionId("pending-partnership-multiple-matches"),
    cryptoAmount = 0.00002,
    txid = null
  )

  val pendingBitcoinTxWithMultipleMatches = BitcoinTransactionMock(
    txid = "pending-bitcoin-multiple-matches-1",
    total = BitcoinMoney.sats(2200),
    fee = BitcoinMoney.sats(200),
    transactionType = BitcoinTransaction.TransactionType.Incoming,
    confirmationTime = null
  )

  val pendingBitcoinTx2WithMultipleMatches = BitcoinTransactionMock(
    txid = "pending-bitcoin-multiple-matches-2",
    total = BitcoinMoney.sats(2200),
    fee = BitcoinMoney.sats(200),
    transactionType = BitcoinTransaction.TransactionType.Incoming,
    confirmationTime = null
  )

  val failedPartnershipTx = pendingPartnershipTx.copy(
    id = PartnershipTransactionId("failed-partnership"),
    txid = "not-a-match",
    status = FAILED
  )

  val confirmedPartnershipTx = confirmedPartnershipTxWithBackupMatch.copy(
    id = PartnershipTransactionId("confirmed-partnership"),
    txid = "not-a-match",
    status = SUCCESS
  )

  val confirmedPartnershipTxWithMatch = pendingPartnershipTxWithMatch.copy(
    id = PartnershipTransactionId("confirmed-partnership-txid-match"),
    txid = "confirmed-bitcoin-txid-match",
    status = SUCCESS
  )

  val confirmedBitcoinTxWithMatch = pendingBitcoinTxWithMatch.copy(
    id = "confirmed-bitcoin-txid-match",
    confirmationStatus = Confirmed(BlockTime(1, someInstant.plus(10.seconds)))
  )

  val confirmedBitcoinTx = pendingBitcoinTx.copy(
    id = "confirmed-bitcoin",
    confirmationStatus = Confirmed(BlockTime(1, someInstant.plus(20.seconds)))
  )

  test("merge by txid") {
    val merged =
      mergeTransactions(listOf(pendingPartnershipTxWithMatch), listOf(pendingBitcoinTxWithMatch))
    merged.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTxWithMatch,
        bitcoinTransaction = pendingBitcoinTxWithMatch
      )
    )
  }

  test("merged by amount + status + type") {
    val merged = mergeTransactions(
      listOf(confirmedPartnershipTxWithBackupMatch),
      listOf(confirmedBitcoinTxWithBackupMatch)
    )
    merged.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTxWithBackupMatch,
        bitcoinTransaction = confirmedBitcoinTxWithBackupMatch
      )
    )
  }

  test("different txids and no matching details do not merge") {
    val merged = mergeTransactions(listOf(pendingPartnershipTx), listOf(pendingBitcoinTx))
    merged.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        details = pendingBitcoinTx
      )
    )
  }

  test("multiple matches by backup criteria does not merge") {
    val merged = mergeTransactions(
      listOf(pendingPartnershipTxWithMultipleMatches),
      listOf(pendingBitcoinTx2WithMultipleMatches, pendingBitcoinTxWithMultipleMatches)
    )
    merged.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTxWithMultipleMatches,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        details = pendingBitcoinTx2WithMultipleMatches
      ),
      Transaction.BitcoinWalletTransaction(
        details = pendingBitcoinTxWithMultipleMatches
      )
    )
  }

  test("sorting transactions by status") {
    val transactions = listOf(
      Transaction.BitcoinWalletTransaction(
        details = confirmedBitcoinTx
      ),
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = failedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTxWithMatch,
        bitcoinTransaction = confirmedBitcoinTxWithMatch
      ),
      Transaction.BitcoinWalletTransaction(
        details = pendingBitcoinTx
      )
    )

    // Expected order:  Pending -> Failed + Completed
    val sorted = sortTransactions(transactions)
    sorted.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        details = pendingBitcoinTx
      ),
      Transaction.PartnershipTransaction(
        details = failedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        details = confirmedBitcoinTx
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTxWithMatch,
        bitcoinTransaction = confirmedBitcoinTxWithMatch
      )
    )
  }

  test("sorting confirmed transactions by confirmation time") {
    val bitcoinTxA = Transaction.BitcoinWalletTransaction(
      details = pendingBitcoinTxWithMatch.copy(
        confirmationStatus = Confirmed(BlockTime(1, someInstant.plus(10.seconds)))
      )
    )
    val bitcoinTxB = Transaction.BitcoinWalletTransaction(
      details = pendingBitcoinTxWithMatch.copy(
        confirmationStatus = Confirmed(BlockTime(1, someInstant))
      )
    )

    val partnershipTransaction = Transaction.PartnershipTransaction(
      details = confirmedPartnershipTx,
      bitcoinTransaction = confirmedBitcoinTx.copy(
        confirmationStatus = Confirmed(BlockTime(1, someInstant.plus(20.seconds)))
      )
    )

    val sorted = sortTransactions(listOf(bitcoinTxA, bitcoinTxB, partnershipTransaction))
    sorted.shouldContainExactly(partnershipTransaction, bitcoinTxA, bitcoinTxB)
  }

  test("sorting pending transactions - created_at for partnership transactions") {
    val partnershipTxA = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMatch.copy(created = someInstant),
      bitcoinTransaction = null
    )
    val partnershipTxB = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMatch.copy(created = someInstant.plus(10.seconds)),
      bitcoinTransaction = null
    )

    val sorted = sortTransactions(listOf(partnershipTxA, partnershipTxB))

    sorted.shouldContainExactly(partnershipTxB, partnershipTxA)
  }

  test("sorting pending transactions - id for bitcoin transactions") {
    val bitcoinTxA = Transaction.BitcoinWalletTransaction(
      details = pendingBitcoinTxWithMultipleMatches.copy(id = "a-id")
    )
    val bitcoinTxB = Transaction.BitcoinWalletTransaction(
      details = pendingBitcoinTxWithMultipleMatches.copy(id = "b-id")
    )

    val sorted = sortTransactions(listOf(bitcoinTxB, bitcoinTxA))

    sorted.shouldContainExactly(bitcoinTxA, bitcoinTxB)
  }

  test("sorting pending transactions - mix of partnership and bitcoin transactions") {
    val bitcoinTx = Transaction.BitcoinWalletTransaction(
      details = pendingBitcoinTxWithMultipleMatches.copy(id = "a-id")
    )
    val partnershipTxA = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMatch.copy(created = someInstant.plus(10.seconds)),
      bitcoinTransaction = null
    )
    val partnershipTxB = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMatch.copy(created = someInstant),
      bitcoinTransaction = null
    )

    val sorted = sortTransactions(listOf(bitcoinTx, partnershipTxA, partnershipTxB))
    sorted.shouldContainExactly(partnershipTxA, partnershipTxB, bitcoinTx)
  }

  test("sorting failed transactions - created_at for partnership transactions") {
    val partnershipTxA = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMultipleMatches.copy(
        created = someInstant,
        status = FAILED
      ),
      bitcoinTransaction = null
    )
    val partnershipTxB = Transaction.PartnershipTransaction(
      details = pendingPartnershipTxWithMultipleMatches.copy(
        created = someInstant.plus(10.seconds),
        status = FAILED
      ),
      bitcoinTransaction = null
    )

    val sorted = sortTransactions(listOf(partnershipTxA, partnershipTxB))

    sorted.shouldContainExactly(partnershipTxB, partnershipTxA)
  }

  test("merge and sort transactions") {
    val transactions = mergeAndSortTransactions(
      listOf(
        pendingPartnershipTxWithMatch,
        pendingPartnershipTx,
        confirmedPartnershipTxWithBackupMatch,
        pendingPartnershipTxWithMultipleMatches,
        failedPartnershipTx,
        confirmedPartnershipTx,
        confirmedPartnershipTxWithMatch
      ),
      listOf(
        pendingBitcoinTxWithMatch,
        pendingBitcoinTx,
        confirmedBitcoinTxWithBackupMatch,
        pendingBitcoinTxWithMultipleMatches,
        confirmedBitcoinTx,
        pendingBitcoinTx2WithMultipleMatches,
        confirmedBitcoinTxWithMatch
      )
    )

    transactions.shouldContainExactly(
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTxWithMatch,
        bitcoinTransaction = pendingBitcoinTxWithMatch
      ),
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = pendingPartnershipTxWithMultipleMatches,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        pendingBitcoinTx
      ),
      Transaction.BitcoinWalletTransaction(
        pendingBitcoinTxWithMultipleMatches
      ),
      Transaction.BitcoinWalletTransaction(
        pendingBitcoinTx2WithMultipleMatches
      ),
      Transaction.PartnershipTransaction(
        details = failedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTx,
        bitcoinTransaction = null
      ),
      Transaction.BitcoinWalletTransaction(
        details = confirmedBitcoinTx
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTxWithMatch,
        bitcoinTransaction = confirmedBitcoinTxWithMatch
      ),
      Transaction.PartnershipTransaction(
        details = confirmedPartnershipTxWithBackupMatch,
        bitcoinTransaction = confirmedBitcoinTxWithBackupMatch
      )
    )
  }
})

package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

fun beIncoming() =
  Matcher<BitcoinTransaction> { value ->
    MatcherResult(
      value.transactionType == Incoming,
      { "expected $value to be incoming" },
      { "expected $value to not be incoming" }
    )
  }

fun BitcoinTransaction.shouldBeIncoming(): BitcoinTransaction {
  this should beIncoming()
  return this
}

fun beOutgoing() =
  Matcher<BitcoinTransaction> { value ->
    MatcherResult(
      value.transactionType == Outgoing,
      { "expected $value to be outgoing" },
      { "expected $value to not be outgoing" }
    )
  }

fun BitcoinTransaction.shouldBeOutgoing(): BitcoinTransaction {
  this should beOutgoing()
  return this
}

fun beConfirmed() =
  Matcher<BitcoinTransaction> { value ->
    MatcherResult(
      value.confirmationStatus is Confirmed,
      { "expected $value to be confirmed" },
      { "expected $value to not be confirmed" }
    )
  }

fun BitcoinTransaction.shouldBeConfirmed(): BitcoinTransaction {
  this should beConfirmed()
  return this
}

fun bePending() =
  Matcher<BitcoinTransaction> { value ->
    MatcherResult(
      value.confirmationStatus == BitcoinTransaction.ConfirmationStatus.Pending,
      { "expected $value to be pending" },
      { "expected $value to not be pending" }
    )
  }

fun BitcoinTransaction.shouldBePending(): BitcoinTransaction {
  this should bePending()
  return this
}

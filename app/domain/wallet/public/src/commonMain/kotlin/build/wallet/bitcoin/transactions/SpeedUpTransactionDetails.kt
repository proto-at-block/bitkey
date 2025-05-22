package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.money.BitcoinMoney
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted

/**
 * Represents subset of [BitcoinTransaction] data necessary for speeding up a bitcoin transaction.
 *
 * We use this to represent a subset of information from a pre-existing transaction that provides
 * information in order to produce a new, speed-up fee rate.
 */
@Redacted
data class SpeedUpTransactionDetails(
  val txid: String,
  val recipientAddress: BitcoinAddress,
  val sendAmount: BitcoinMoney,
  val oldFee: Fee,
  @Unredacted
  val transactionType: BitcoinTransaction.TransactionType,
)

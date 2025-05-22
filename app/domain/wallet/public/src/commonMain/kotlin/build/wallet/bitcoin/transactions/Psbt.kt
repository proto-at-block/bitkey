package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import dev.zacsweers.redacted.annotations.Redacted

/**
 * Partially Signed Bitcoin Transaction.
 */
@Redacted
data class Psbt(
  /** The ID of the transaction */
  val id: String,
  /** The encoded representation of the transaction */
  val base64: String,
  /** The fee associated with the transaction in [Money], which must have currency in [BTC] */
  val fee: BitcoinMoney,
  /** The size of the transaction in bytes, without witnesses */
  val baseSize: Long,
  /** The number of inputs associated with the transaction */
  val numOfInputs: Int,
  /** Amount of sats to send in transaction */
  val amountSats: ULong,
  /** The inputs associated with the transaction */
  val inputs: Set<BdkTxIn> = emptySet(),
  /** The outputs associated with the transaction */
  val outputs: Set<BdkTxOut> = emptySet(),
) {
  /** Amount of bitcoin to send in transaction */
  val amountBtc: BitcoinMoney get() = BitcoinMoney.sats(amountSats.toLong())
}

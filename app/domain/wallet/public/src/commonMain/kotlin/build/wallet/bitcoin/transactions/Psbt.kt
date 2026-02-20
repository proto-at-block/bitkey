package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.fees.Fee
import build.wallet.money.BitcoinMoney
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
  /** The [fee] associated with the transaction represented as [Fee], which must have currency in [BTC] */
  val fee: Fee,
  /**
   * The virtual size (vsize) of the transaction in virtual bytes.
   * For segwit transactions: vsize = ceil(weight / 4).
   * This is the correct value to use for fee rate calculations.
   */
  val vsize: Long,
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

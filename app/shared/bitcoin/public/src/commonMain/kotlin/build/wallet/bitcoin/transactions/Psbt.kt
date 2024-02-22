package build.wallet.bitcoin.transactions

import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC

/**
 * Partially Signed Bitcoin Transaction.
 */
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
)

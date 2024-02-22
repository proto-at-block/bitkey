package build.wallet.bdk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.29.0/bdk-ffi/src/bdk.udl#L97
 */
data class BdkTransactionDetails(
  val transaction: BdkTransaction?,
  val fee: BigInteger?,
  val received: BigInteger,
  val sent: BigInteger,
  val txid: String,
  val confirmationTime: BdkBlockTime?,
) {
  /**
   *  Constructor for easier interop without exposing BigInteger APIs.
   */
  constructor(
    transaction: BdkTransaction?,
    fee: ULong?,
    received: ULong,
    sent: ULong,
    txid: String,
    confirmationTime: BdkBlockTime?,
  ) : this(
    transaction = transaction,
    fee = fee?.toBigInteger(),
    received = received.toBigInteger(),
    sent = sent.toBigInteger(),
    txid = txid,
    confirmationTime = confirmationTime
  )
}

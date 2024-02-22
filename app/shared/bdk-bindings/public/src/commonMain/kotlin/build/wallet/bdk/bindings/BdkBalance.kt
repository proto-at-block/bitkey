package build.wallet.bdk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L81
 */
data class BdkBalance(
  val immature: BigInteger,
  val trustedPending: BigInteger,
  val untrustedPending: BigInteger,
  val confirmed: BigInteger,
  val spendable: BigInteger,
  val total: BigInteger,
) {
  /**
   * Constructor for easier interop without exposing BigInteger APIs.
   */
  constructor(
    immature: ULong,
    trustedPending: ULong,
    untrustedPending: ULong,
    confirmed: ULong,
    spendable: ULong,
    total: ULong,
  ) : this(
    immature = immature.toBigInteger(),
    trustedPending = trustedPending.toBigInteger(),
    untrustedPending = untrustedPending.toBigInteger(),
    confirmed = confirmed.toBigInteger(),
    spendable = spendable.toBigInteger(),
    total = total.toBigInteger()
  )
}

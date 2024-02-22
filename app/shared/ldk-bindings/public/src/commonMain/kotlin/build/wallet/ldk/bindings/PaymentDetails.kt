package build.wallet.ldk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger

data class PaymentDetails(
  val paymentHash: PaymentHash,
  val preimage: PaymentPreimage?,
  val secret: PaymentSecret?,
  val amountMsat: BigInteger?,
  val direction: PaymentDirection,
  val paymentStatus: PaymentStatus,
)

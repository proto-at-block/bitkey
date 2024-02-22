package build.wallet.ldk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger

sealed class LdkEvent {
  data class PaymentSuccessful(
    val paymentHash: PaymentHash,
  ) : LdkEvent()

  data class PaymentFailed(
    val paymentHash: PaymentHash,
  ) : LdkEvent()

  data class PaymentReceived(
    val paymentHash: PaymentHash,
    val amountMsat: BigInteger,
  ) : LdkEvent()

  data class ChannelReady(
    val channelId: ChannelId,
    val userChannelId: UserChannelId,
  ) : LdkEvent()

  data class ChannelClosed(
    val channelId: ChannelId,
    val userChannelId: UserChannelId,
  ) : LdkEvent()

  data class ChannelPending(
    val channelId: ChannelId,
    val userChannelId: UserChannelId,
    val formerTemporaryChannelId: ChannelId,
    val counterpartyNodeId: PublicKey,
    val fundingTxo: OutPoint,
  ) : LdkEvent()
}

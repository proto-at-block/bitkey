package build.wallet.ldk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger

@Suppress("TooManyFunctions")
interface LdkNode {
  fun start(): LdkResult<Unit>

  fun stop(): LdkResult<Unit>

  fun syncWallets(): LdkResult<Unit>

  fun nextEvent(): LdkEvent

  fun nodeId(): PublicKey

  fun newFundingAddress(): LdkResult<Address>

  fun spendableOnchainBalanceSats(): LdkResult<BigInteger>

  fun totalOnchainBalanceSats(): LdkResult<BigInteger>

  // Channel-Related Operations
  fun connectOpenChannel(
    nodePublicKey: PublicKey,
    address: SocketAddr,
    channelAmountSats: BigInteger,
    announceChannel: Boolean,
  ): LdkResult<Unit>

  fun closeChannel(
    channelId: ChannelId,
    counterpartyNodeId: ChannelId,
  ): LdkResult<Unit>

  // Payment-Related Operations
  fun sendPayment(invoice: Invoice): LdkResult<PaymentHash>

  fun sendPaymentUsingAmount(
    invoice: Invoice,
    amountMsat: BigInteger,
  ): LdkResult<PaymentHash>

  fun sendSpontaneousPayment(
    amountMsat: BigInteger,
    nodeId: String,
  ): LdkResult<PaymentHash>

  fun receivePayment(
    amountMsat: BigInteger,
    description: String,
    expirySecs: Long,
  ): LdkResult<Invoice>

  fun receiveVariableAmountPayment(
    description: String,
    expirySecs: Long,
  ): LdkResult<Invoice>

  fun paymentInfo(paymentHash: PaymentHash): PaymentDetails?

  fun listChannels(): List<ChannelDetails>

  fun connectPeer(
    nodeId: PublicKey,
    address: SocketAddr,
    permanently: Boolean,
  ): LdkResult<Unit>
}

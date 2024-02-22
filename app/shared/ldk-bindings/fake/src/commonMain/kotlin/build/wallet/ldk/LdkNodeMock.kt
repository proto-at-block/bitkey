package build.wallet.ldk

import build.wallet.ldk.bindings.Address
import build.wallet.ldk.bindings.ChannelDetails
import build.wallet.ldk.bindings.ChannelId
import build.wallet.ldk.bindings.Invoice
import build.wallet.ldk.bindings.LdkEvent
import build.wallet.ldk.bindings.LdkNode
import build.wallet.ldk.bindings.LdkResult
import build.wallet.ldk.bindings.LdkResult.Ok
import build.wallet.ldk.bindings.PaymentDetails
import build.wallet.ldk.bindings.PaymentHash
import build.wallet.ldk.bindings.PublicKey
import build.wallet.ldk.bindings.SocketAddr
import com.ionspin.kotlin.bignum.integer.BigInteger

data class LdkNodeMock(
  var startResult: LdkResult<Unit> = Ok(Unit),
  var stopResult: LdkResult<Unit> = Ok(Unit),
  var syncWalletsResult: LdkResult<Unit> = Ok(Unit),
  var nextEvent: LdkEvent = LdkEvent.PaymentSuccessful(paymentHash = "abcdef"),
  var nodeId: PublicKey = "abcdef",
  var fundingAddressResult: LdkResult<Address> = Ok("tb123456"),
  var spendableOnchainBalanceSatsResult: LdkResult<BigInteger> = Ok(BigInteger(10000)),
  var totalOnchainBalanceSatsResult: LdkResult<BigInteger> = Ok(BigInteger(10000)),
  var connectOpenChannelResult: LdkResult<Unit> = Ok(Unit),
  var closeChannelResult: LdkResult<Unit> = Ok(Unit),
  var sendPaymentResult: LdkResult<PaymentHash> = Ok("abcdef"),
  var sendPaymentUsingAmountResult: LdkResult<PaymentHash> = Ok("abcdef"),
  var sendSpontaneousPaymentResult: LdkResult<PaymentHash> = Ok("abcdef"),
  var receivePaymentResult: LdkResult<Invoice> = Ok("ln123456"),
  var receiveVariableAmountPaymentResult: LdkResult<Invoice> = Ok("ln123456"),
  var paymentDetailsResult: PaymentDetails? = null,
  var channels: List<ChannelDetails> = emptyList(),
  var connectPeerResult: LdkResult<Unit> = Ok(Unit),
) : LdkNode {
  override fun start(): LdkResult<Unit> = startResult

  override fun stop(): LdkResult<Unit> = stopResult

  override fun syncWallets(): LdkResult<Unit> = syncWalletsResult

  override fun nextEvent(): LdkEvent = nextEvent

  override fun nodeId(): PublicKey = nodeId

  override fun newFundingAddress(): LdkResult<Address> = fundingAddressResult

  override fun spendableOnchainBalanceSats(): LdkResult<BigInteger> =
    spendableOnchainBalanceSatsResult

  override fun totalOnchainBalanceSats(): LdkResult<BigInteger> = totalOnchainBalanceSatsResult

  override fun connectOpenChannel(
    nodePublicKey: PublicKey,
    address: SocketAddr,
    channelAmountSats: BigInteger,
    announceChannel: Boolean,
  ): LdkResult<Unit> = connectOpenChannelResult

  override fun closeChannel(
    channelId: ChannelId,
    counterpartyNodeId: ChannelId,
  ): LdkResult<Unit> = closeChannelResult

  override fun sendPayment(invoice: Invoice): LdkResult<PaymentHash> = sendPaymentResult

  override fun sendPaymentUsingAmount(
    invoice: Invoice,
    amountMsat: BigInteger,
  ): LdkResult<PaymentHash> = sendPaymentUsingAmountResult

  override fun sendSpontaneousPayment(
    amountMsat: BigInteger,
    nodeId: String,
  ): LdkResult<PaymentHash> = sendSpontaneousPaymentResult

  override fun receivePayment(
    amountMsat: BigInteger,
    description: String,
    expirySecs: Long,
  ): LdkResult<Invoice> = receivePaymentResult

  override fun receiveVariableAmountPayment(
    description: String,
    expirySecs: Long,
  ): LdkResult<Invoice> = receiveVariableAmountPaymentResult

  override fun paymentInfo(paymentHash: PaymentHash): PaymentDetails? = paymentDetailsResult

  override fun listChannels(): List<ChannelDetails> = channels

  override fun connectPeer(
    nodeId: PublicKey,
    address: SocketAddr,
    permanently: Boolean,
  ): LdkResult<Unit> = connectPeerResult
}

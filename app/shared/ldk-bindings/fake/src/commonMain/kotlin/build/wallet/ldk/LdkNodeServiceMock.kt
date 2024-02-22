package build.wallet.ldk

import build.wallet.ldk.bindings.ChannelDetails
import build.wallet.ldk.bindings.Invoice
import build.wallet.ldk.bindings.LdkResult
import build.wallet.ldk.bindings.LdkResult.Ok
import build.wallet.ldk.bindings.PaymentHash
import build.wallet.ldk.bindings.PublicKey
import com.ionspin.kotlin.bignum.integer.BigInteger

data class LdkNodeServiceMock(
  var ldkNodeMock: LdkNodeMock = LdkNodeMock(),
  val spendableOnchainBalanceSatsResult: LdkResult<BigInteger> = Ok(BigInteger(10000)),
  val getFundingAddressResult: LdkResult<String> = Ok("abcdef"),
  val syncWalletResult: LdkResult<Unit> = Ok(Unit),
  val connectAndOpenChannelResult: LdkResult<Unit> = Ok(Unit),
  val listChannelsResult: List<ChannelDetails> = emptyList(),
  val sendPaymentResult: LdkResult<PaymentHash> = Ok(PaymentHash()),
  val startResult: LdkResult<Unit> = Ok(Unit),
  val nodeIdResult: LdkResult<PublicKey> = Ok(PublicKey()),
  val connectToLspResult: LdkResult<Unit> = Ok(Unit),
  val closeChannelResult: LdkResult<Unit> = Ok(Unit),
  val receivePaymentResult: LdkResult<Invoice> = Ok(Invoice()),
  val totalLightningBalanceResult: BigInteger = BigInteger(10000),
) : LdkNodeService {
  override fun start(): LdkResult<Unit> = startResult

  override fun nodeId(): LdkResult<PublicKey> = nodeIdResult

  override fun spendableOnchainBalance(): LdkResult<BigInteger> = spendableOnchainBalanceSatsResult

  override fun getFundingAddress(): LdkResult<String> = getFundingAddressResult

  override fun syncWallets(): LdkResult<Unit> = syncWalletResult

  override fun connectAndOpenChannel(
    nodePublicKey: String,
    address: String,
    channelAmountSats: BigInteger,
  ): LdkResult<Unit> = connectAndOpenChannelResult

  override fun closeChannel(channel: ChannelDetails): LdkResult<Unit> = closeChannelResult

  override fun listChannels(): List<ChannelDetails> = listChannelsResult

  override fun sendPayment(invoice: Invoice): LdkResult<PaymentHash> = sendPaymentResult

  override fun receivePayment(
    amountMsat: BigInteger,
    description: String,
  ): LdkResult<Invoice> = receivePaymentResult

  override fun totalLightningBalance(): BigInteger = totalLightningBalanceResult

  override fun connectToLsp(): LdkResult<Unit> = connectToLspResult
}

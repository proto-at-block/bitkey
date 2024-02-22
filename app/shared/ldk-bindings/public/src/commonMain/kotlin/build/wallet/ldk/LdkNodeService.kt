package build.wallet.ldk

import build.wallet.ldk.bindings.ChannelDetails
import build.wallet.ldk.bindings.Invoice
import build.wallet.ldk.bindings.LdkResult
import build.wallet.ldk.bindings.PaymentHash
import build.wallet.ldk.bindings.PublicKey
import com.ionspin.kotlin.bignum.integer.BigInteger

@Suppress("TooManyFunctions")
interface LdkNodeService {
  fun start(): LdkResult<Unit>

  fun nodeId(): LdkResult<PublicKey>

  fun spendableOnchainBalance(): LdkResult<BigInteger>

  fun getFundingAddress(): LdkResult<String>

  fun syncWallets(): LdkResult<Unit>

  fun connectAndOpenChannel(
    nodePublicKey: String,
    address: String,
    channelAmountSats: BigInteger,
  ): LdkResult<Unit>

  fun closeChannel(channel: ChannelDetails): LdkResult<Unit>

  fun listChannels(): List<ChannelDetails>

  fun sendPayment(invoice: Invoice): LdkResult<PaymentHash>

  fun receivePayment(
    amountMsat: BigInteger,
    description: String,
  ): LdkResult<Invoice>

  fun totalLightningBalance(): BigInteger

  fun connectToLsp(): LdkResult<Unit>
}

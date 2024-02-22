package build.wallet.ldk.bindings

data class OutPoint(
  val txid: String,
  val index: UInt,
)

data class ChannelDetails(
  val channelId: ChannelId,
  val counterparty: PublicKey,
  val fundingTxo: OutPoint?,
  val shortChannelId: ULong?,
  val channelValueSatoshis: ULong,
  val balanceMsat: ULong,
  val outboundCapacityMsat: ULong,
  val inboundCapacityMsat: ULong,
  val confirmationsRequired: UInt?,
  val confirmations: UInt?,
  val isOutbound: Boolean,
  val isChannelReady: Boolean,
  val isUsable: Boolean,
  val isPublic: Boolean,
  val cltvExpiryDelta: UShort?,
)

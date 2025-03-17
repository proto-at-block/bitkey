package build.wallet.bdk.bindings

data class BdkOutPoint(
  val txid: String,
  val vout: UInt,
)

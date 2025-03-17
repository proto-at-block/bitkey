package build.wallet.bdk.bindings

data class BdkUtxo(
  val outPoint: BdkOutPoint,
  val txOut: BdkTxOut,
  val isSpent: Boolean,
)

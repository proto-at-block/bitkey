package build.wallet.bdk.bindings

/**
 * https://docs.rs/bitcoin/latest/bitcoin/blockdata/transaction/struct.TxOut.html
 */
data class BdkTxOut(
  val value: ULong,
  val scriptPubkey: BdkScript,
)

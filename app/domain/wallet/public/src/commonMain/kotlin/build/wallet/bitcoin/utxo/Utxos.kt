package build.wallet.bitcoin.utxo

import build.wallet.bdk.bindings.BdkUtxo

/**
 * UTXOs that belong to the wallet, grouped by confirmation status of the transaction each
 * UTXO belongs to.
 *
 * @property confirmed UTXOs that belong to confirmed transactions.
 * @property unconfirmed UTXOs that belong to unconfirmed transactions.
 */
data class Utxos(
  val confirmed: Set<BdkUtxo>,
  val unconfirmed: Set<BdkUtxo>,
) {
  init {
    require(confirmed.intersect(unconfirmed).isEmpty()) {
      "UTXOs cannot be both confirmed and unconfirmed."
    }
  }

  val all: Set<BdkUtxo> get() = confirmed + unconfirmed
}

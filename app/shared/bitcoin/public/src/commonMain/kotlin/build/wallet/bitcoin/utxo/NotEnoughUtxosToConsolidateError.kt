package build.wallet.bitcoin.utxo

/**
 * Error indicating that customer cannot consolidate UTXOs because there are not enough UTXOs to consolidate.
 * Customer must have at least 2 UTXOs to consolidate.
 */
data class NotEnoughUtxosToConsolidateError(val utxoCount: Int) : Error() {
  init {
    require(utxoCount == 0 || utxoCount == 1)
  }
}

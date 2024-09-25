package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.money.BitcoinMoney

/**
 * @param type describes the type of this UTXO consolidation.
 * @param targetAddress the address to which the consolidated UTXOs will be sent.
 * @param currentUtxoCount current number of unspent transaction outputs. Has to be
 * greater than 1 in order to consolidate.
 * @param balance wallet's balance that can be consolidated (total unspent).
 * @param consolidationCost estimated cost of consolidation transaction.
 * @param appSignedPsbt the PSBT for the consolidation that needs to be signed
 * with hardware and broadcasted in order to consolidate UTXOs.
 */
data class UtxoConsolidationParams(
  val type: UtxoConsolidationType,
  val targetAddress: BitcoinAddress,
  val currentUtxoCount: Int,
  val balance: BitcoinMoney,
  val consolidationCost: BitcoinMoney,
  val appSignedPsbt: Psbt,
) {
  init {
    require(currentUtxoCount > 1) { "currentUtxoCount must be greater than 1" }
  }
}

package build.wallet.bitcoin.utxo

import build.wallet.money.BitcoinMoney

sealed interface UtxoConsolidationType {
  /**
   * Describes UTXO consolidation where all UTXOs are consolidated into a single UTXO.
   */
  data object ConsolidateAll : UtxoConsolidationType

  /**
   * Describes UTXO consolidation where UTXOs smaller than [maxValue] are consolidated
   * into sufficiently large UTXOs.
   */
  data class MaxValuePerUtxo(val maxValue: BitcoinMoney) : UtxoConsolidationType
}

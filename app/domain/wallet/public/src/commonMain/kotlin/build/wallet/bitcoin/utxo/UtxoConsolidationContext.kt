package build.wallet.bitcoin.utxo

/**
 * Context for why UTXO consolidation is being performed.
 * Used throughout the consolidation flow to customize behavior based on the scenario.
 */
sealed interface UtxoConsolidationContext {
  /**
   * Standard UTXO consolidation initiated by the user from settings or home screen.
   */
  data object Standard : UtxoConsolidationContext

  /**
   * UTXO consolidation required before private wallet migration. Will loop through consolidations
   * until no more are required.
   */
  data object PrivateWalletMigration : UtxoConsolidationContext
}

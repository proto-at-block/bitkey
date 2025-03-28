package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.transactions.Psbt
import com.github.michaelbull.result.Result

/**
 * Domain service for managing UTXO consolidation.
 */
interface UtxoConsolidationService {
  /**
   * Returns list of [UtxoConsolidationParams] each containing necessary data for UTXO consolidation:
   * number of UTXOs, consolidation balance, consolidation cost, unsigned PSBT, etc.
   *
   * Currently, only returns list with single [UtxoConsolidationParams] of type [UtxoConsolidationType.ConsolidateAll].
   * TODO(W-9710): implement support for different consolidation types.
   *
   * A consolidation transaction will need to be signed with the customer's hardware.
   *
   * If there are not enough UTXOs to consolidate (less than 2), [NotEnoughUtxosToConsolidateError]
   * is returned.
   */
  suspend fun prepareUtxoConsolidation(): Result<List<UtxoConsolidationParams>, Throwable>

  /**
   * Broadcasts fully signed consolidation transaction.
   */
  suspend fun broadcastConsolidation(
    signedConsolidation: Psbt,
  ): Result<UtxoConsolidationTransactionDetail, Error>
}

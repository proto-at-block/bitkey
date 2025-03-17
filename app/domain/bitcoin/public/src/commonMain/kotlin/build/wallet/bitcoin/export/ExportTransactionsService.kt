package build.wallet.bitcoin.export

import com.github.michaelbull.result.Result

/*
 * Domain service for exporting transaction history.
 */
interface ExportTransactionsService {
  /*
   * Returns a CSV blob of the user's transaction history.
   *
   * Each row is deserializable to [ExportTransactionRow]
   */
  suspend fun export(): Result<ExportedTransactions, Throwable>
}

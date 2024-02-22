package build.wallet.bitcoin.transactions

import build.wallet.db.DbError
import com.github.michaelbull.result.Result

/**
 * An abstraction layer for reading and writing transaction information and their associated
 * exchange rates.
 */
interface TransactionRepository {
  /**
   * Persist transaction and its associated set of exchange rate at the time of broadcast.
   */
  suspend fun setTransaction(transaction: Transaction): Result<Unit, DbError>
}

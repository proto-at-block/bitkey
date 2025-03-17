package build.wallet.sqldelight

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import build.wallet.catchingResult
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.coroutines.BitkeyDatabaseIO
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * A suspending wrapper around [Transacter.transaction] that returns a [Result] while catching
 * runtime exceptions.
 *
 * Executes transaction on [Dispatchers.BitkeyDatabaseIO] by default.
 */
suspend fun <TransacterT : Transacter> TransacterT.awaitTransaction(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
  body: TransacterT.(TransactionWithoutReturn) -> Unit,
): Result<Unit, DbTransactionError> {
  return catchingResult {
    withContext(context) {
      transaction {
        body(this)
      }
    }
  }
    .mapError { DbTransactionError(it) }
}

/**
 * A suspending wrapper around [Transacter.transactionWithResult] that returns a [Result] while
 * catching runtime exceptions.
 * Executes transaction on [Dispatchers.BitkeyDatabaseIO] by default.
 */
suspend fun <TransacterT : Transacter, ReturnT> TransacterT.awaitTransactionWithResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
  body: TransacterT.(TransactionWithReturn<ReturnT>) -> ReturnT,
): Result<ReturnT, DbTransactionError> {
  return catchingResult {
    withContext(context) {
      transactionWithResult {
        body(this)
      }
    }
  }
    .mapError { DbTransactionError(it) }
}

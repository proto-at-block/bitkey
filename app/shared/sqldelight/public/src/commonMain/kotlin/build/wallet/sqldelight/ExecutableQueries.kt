package build.wallet.sqldelight

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import build.wallet.catching
import build.wallet.db.DbQueryError
import build.wallet.sqldelight.coroutines.BitkeyDatabaseIO
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Same as [awaitAsList] async extension but returns [Result] and wraps any thrown exceptions into
 * [DbQueryError].
 */
suspend fun <T : Any> ExecutableQuery<T>.awaitAsListResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Result<List<T>, DbQueryError> =
  Result
    .catching { withContext(context) { awaitAsList() } }
    .mapError { DbQueryError(cause = it) }

/**
 * Same as [awaitAsOne] async extension but returns [Result] and wraps any thrown exceptions into
 * [DbQueryError].
 */
suspend fun <T : Any> ExecutableQuery<T>.awaitAsOneResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Result<T, DbQueryError> =
  Result
    .catching { withContext(context) { awaitAsOne() } }
    .mapError { DbQueryError(cause = it) }

/**
 * Same as [awaitAsOneOrNull] async extension but returns [Result] and wraps any thrown exceptions
 * into [DbQueryError].
 */
suspend fun <T : Any> ExecutableQuery<T>.awaitAsOneOrNullResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Result<T?, DbQueryError> =
  Result
    .catching { withContext(context) { awaitAsOneOrNull() } }
    .mapError { DbQueryError(cause = it) }

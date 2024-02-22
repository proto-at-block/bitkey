package build.wallet.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import build.wallet.LoadableValue
import build.wallet.asLoadableValue
import build.wallet.db.DbQueryError
import build.wallet.sqldelight.coroutines.BitkeyDatabaseIO
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * Returns [Flow] of [List] of [T] from the [Query].
 * Use this if the query is supposed to return list of results.
 *
 * Executes the database query on the [Dispatchers.BitkeyDatabaseIO].
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError]. Note that once
 * this happens, the flow will complete and will no longer emit any new values. See [Flow.catch].
 * TODO(W-4031): reconsider this behavior, maybe we should just emit [Err] and keep the flow alive?
 */
internal fun <T : Any> Flow<Query<T>>.mapToListResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Flow<Result<LoadableValue<List<T>>, DbQueryError>> =
  mapToList(context)
    .map {
      @Suppress("USELESS_CAST")
      Ok(it) as Result<List<T>, DbQueryError>
    }
    .asLoadableValue()
    .catch { emit(Err(DbQueryError(cause = it))) }

/**
 * Returns [Flow] of of non-nullable [T] from the [Query].
 * Use this if the query is supposed to return single, non-nullable result.
 *
 * Executes the query on the [Dispatchers.BitkeyDatabaseIO].
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError]. Note that once
 * this happens, the flow will complete and will no longer emit any new values. See [Flow.catch].
 */
internal fun <T : Any> Flow<Query<T>>.mapToOneResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Flow<Result<LoadableValue<T>, DbQueryError>> =
  mapToOne(context)
    .map {
      @Suppress("USELESS_CAST")
      Ok(it) as Result<T, DbQueryError>
    }
    .asLoadableValue()
    .catch { emit(Err(DbQueryError(cause = it))) }

/**
 * Use this if the query is supposed to return single, potentially nullable, result.
 *
 * Executes the query on the [Dispatchers.BitkeyDatabaseIO] by default since this is what we want in most
 * cases anyway.
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError]. Note that once
 * this happens, the flow will complete and will no longer emit any new values. See [Flow.catch].
 */
internal fun <T : Any> Flow<Query<T>>.mapToOneOrNullResult(
  context: CoroutineContext = Dispatchers.BitkeyDatabaseIO,
): Flow<Result<LoadableValue<T?>, DbQueryError>> =
  mapToOneOrNull(context)
    .map {
      @Suppress("USELESS_CAST")
      Ok(it) as Result<T?, DbQueryError>
    }
    .asLoadableValue()
    .catch { emit(Err(DbQueryError(cause = it))) }

package build.wallet.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import build.wallet.db.DbQueryError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Returns [Flow] of [List] of [T] from the [Query].
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError].
 */
fun <T : Any> Query<T>.asFlowOfList(): Flow<Result<List<T>, DbQueryError>> =
  asFlow().mapToListResult()

/**
 * Returns [Flow] of of nullable [T] from the [Query].
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError].
 */
fun <T : Any> Query<T>.asFlowOfOneOrNull(): Flow<Result<T?, DbQueryError>> =
  asFlow().mapToOneOrNullResult()

/**
 * Returns [Flow] of of non-nullable [T] from the [Query].
 *
 * Any exceptions caught while executing the query are wrapped into [DbQueryError].
 */
fun <T : Any> Query<T>.asFlowOfOne(): Flow<Result<T, DbQueryError>> = asFlow().mapToOneResult()

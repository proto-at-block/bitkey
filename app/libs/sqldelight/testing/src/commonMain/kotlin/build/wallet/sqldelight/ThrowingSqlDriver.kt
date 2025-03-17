package build.wallet.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * A [SqlDriver] which throws a fake exception on any query execution.
 */
object ThrowingSqlDriver : SqlDriver {
  val QUERY_ERROR = IllegalStateException("SqlDriver query error")

  override fun close() = Unit

  override fun addListener(
    vararg queryKeys: String,
    listener: Query.Listener,
  ) = Unit

  override fun currentTransaction(): Transacter.Transaction? = null

  override fun execute(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<Long> {
    throw QUERY_ERROR
  }

  override fun <R> executeQuery(
    identifier: Int?,
    sql: String,
    mapper: (SqlCursor) -> QueryResult<R>,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<R> {
    throw QUERY_ERROR
  }

  override fun newTransaction(): QueryResult<Transacter.Transaction> {
    throw QUERY_ERROR
  }

  override fun notifyListeners(vararg queryKeys: String) = Unit

  override fun removeListener(
    vararg queryKeys: String,
    listener: Query.Listener,
  ) = Unit
}

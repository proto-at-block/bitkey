package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.logs.LogSqliteDriver
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.logError
import build.wallet.logging.logVerbose

/**
 * Adds logging to this [SqlDriver] instance:
 *
 * - Logs all SqlDriver operations using [Debug] level.
 * - Logs query errors using [Error] level, which will be reported to crash analytics.
 *
 * Delegates [this] implementation of [SqlDriver] to [SqlDriverWithErrorLogging].
 *
 * @param tag optional, will be used for log messages for this [SqlDriver]. If not provided,
 * default app tag will be used.
 */
fun SqlDriver.withLogging(tag: String? = null): SqlDriver {
  return LogSqliteDriver(
    sqlDriver = SqlDriverWithErrorLogging(delegate = this),
    logger = { message -> logVerbose(tag, message = { message }) }
  )
}

/**
 * Delegate for [SqlDriver] which logs errors occurred during execution of a query.
 */
@Suppress("TooGenericExceptionCaught")
private class SqlDriverWithErrorLogging(
  private val delegate: SqlDriver,
  private val tag: String? = null,
) : SqlDriver by delegate {
  override fun execute(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<Long> {
    return try {
      delegate.execute(
        identifier = identifier,
        sql = sql,
        parameters = parameters,
        binders = binders
      )
    } catch (e: Throwable) {
      logError(tag, throwable = e) { "Sqlite error executing query \"$sql\"" }
      throw e
    }
  }
}

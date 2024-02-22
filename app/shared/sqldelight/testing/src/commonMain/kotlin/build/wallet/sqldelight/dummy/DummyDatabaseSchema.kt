package build.wallet.sqldelight.dummy

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.QueryResult.Value
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * [SqlSchema] for the dummy database.
 */
internal object DummyDatabaseSchema : SqlSchema<Value<Unit>> {
  override val version: Long = 1

  override fun create(driver: SqlDriver): Value<Unit> {
    driver.execute(
      identifier = null,
      sql =
        """
        CREATE TABLE dummy(
          id INTEGER NOT NULL PRIMARY KEY,
          value TEXT NOT NULL
        );
        """.trimIndent(),
      parameters = 0
    )
    return QueryResult.Unit
  }

  // noop, no migration logic.
  override fun migrate(
    driver: SqlDriver,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: AfterVersion,
  ): Value<Unit> = QueryResult.Unit
}

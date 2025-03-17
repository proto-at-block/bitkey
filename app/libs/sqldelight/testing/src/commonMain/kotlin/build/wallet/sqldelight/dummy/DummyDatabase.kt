package build.wallet.sqldelight.dummy

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Dummy database schema for SqlDelight queries.
 *
 * Imitates SqlDelight generated database class, if we had a `dummy.sq` and used SqlDelight plugin.
 */
class DummyDatabase(val sqlDriver: SqlDriver) {
  val dummyDataQueries = DummyDataEntityQueries(sqlDriver)

  companion object {
    val Schema: SqlSchema<QueryResult.Value<Unit>> = DummyDatabaseSchema
  }
}

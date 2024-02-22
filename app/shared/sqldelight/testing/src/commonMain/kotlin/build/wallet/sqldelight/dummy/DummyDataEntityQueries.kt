package build.wallet.sqldelight.dummy

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import build.wallet.sqldelight.ThrowingSqlDriver

/**
 * Imitates SqlDelight's generated queries class, if we had a `dummy.sq` and used SqlDelight plugin.
 */
class DummyDataEntityQueries(val sqlDriver: SqlDriver) {
  /**
   * Inserts a row into `dummy` table.
   */
  suspend fun insertDummyData(
    id: Long,
    value: String,
  ) {
    sqlDriver.execute(
      identifier = null,
      sql =
        """
        INSERT INTO dummy (id, value)
        VALUES (?, ?)
        """.trimIndent(),
      parameters = 2,
      binders = {
        bindLong(0, id)
        bindString(1, value)
      }
    ).await()
  }

  /**
   * Deletes all rows from `dummy` table.
   */
  fun clear() {
    sqlDriver.execute(identifier = null, sql = "DELETE FROM dummy", parameters = 0)
  }

  /**
   * Returns all rows from `dummy` table.
   */
  fun getDummyData(throwError: Boolean = false): Query<DummyDataEntity> =
    Query(
      identifier = 0,
      queryKeys = arrayOf("dummyDataEntity"),
      driver = if (throwError) ThrowingSqlDriver else sqlDriver,
      fileName = "dummy",
      label = "queryDummyData",
      query = "SELECT * FROM dummy",
      mapper = mapper
    )

  private val mapper = { cursor: SqlCursor ->
    DummyDataEntity(
      cursor.getLong(0)!!,
      cursor.getString(1)!!
    )
  }
}

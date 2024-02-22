package build.wallet.sqldelight

import build.wallet.sqldelight.dummy.DummyDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class DatabaseContentsTests : FunSpec({
  val sqlDriverFactory = inMemorySqlDriver().factory

  val sqlDriver =
    sqlDriverFactory.createDriver(
      dataBaseName = "foods.db",
      dataBaseSchema = DummyDatabase.Schema
    )
  val testDataQueries = DummyDatabase(sqlDriver).dummyDataQueries

  test("empty database contents") {
    sqlDriver.databaseContents().tables.shouldContainExactly(
      DatabaseContents.TableContents(
        tableName = "dummy",
        columnNames = listOf("id", "value"),
        rowValues =
          mapOf(
            "id" to emptyList(),
            "value" to emptyList()
          )
      )
    )
  }

  test("database with contents") {
    testDataQueries.insertDummyData(id = 0, "a")
    testDataQueries.insertDummyData(id = 1, "b")
    testDataQueries.insertDummyData(id = 2, "c")

    sqlDriver.databaseContents().tables.shouldContainExactly(
      DatabaseContents.TableContents(
        tableName = "dummy",
        columnNames = listOf("id", "value"),
        rowValues =
          mapOf(
            "id" to listOf("0", "1", "2"),
            "value" to listOf("a", "b", "c")
          )
      )
    )
  }
})

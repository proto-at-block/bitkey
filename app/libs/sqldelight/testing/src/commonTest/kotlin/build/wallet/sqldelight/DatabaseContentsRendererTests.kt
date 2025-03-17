package build.wallet.sqldelight

import app.cash.sqldelight.db.SqlDriver
import build.wallet.sqldelight.dummy.DummyDataEntityQueries
import build.wallet.sqldelight.dummy.DummyDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DatabaseContentsRendererTests : FunSpec({

  lateinit var sqlDriver: SqlDriver
  lateinit var testDataQueries: DummyDataEntityQueries
  beforeTest {
    val sqlDriverFactory = inMemorySqlDriver().factory
    sqlDriver = sqlDriverFactory.createDriver(
      dataBaseName = "test.db",
      dataBaseSchema = DummyDatabase.Schema
    )
    testDataQueries = DummyDatabase(sqlDriver).dummyDataQueries
  }

  afterTest {
    sqlDriver.close()
  }

  test("empty database") {
    sqlDriver.databaseContents().renderText()
      .shouldBe(
        """
        |┌────────┐
        |│ dummy  │
        |├──┬─────┤
        |│id│value│
        |├──┴─────┤
        |│ Empty  │
        |└────────┘
        """.trimMargin()
      )
  }

  test("database with contents") {
    testDataQueries.insertDummyData(id = 0, "a")
    testDataQueries.insertDummyData(id = 1, "b")
    testDataQueries.insertDummyData(id = 2, "c")

    sqlDriver.databaseContents().renderText().shouldBe(
      """
      |┌────────┐
      |│ dummy  │
      |├──┬─────┤
      |│id│value│
      |├──┼─────┤
      |│0 │  a  │
      |├──┼─────┤
      |│1 │  b  │
      |├──┼─────┤
      |│2 │  c  │
      |└──┴─────┘
      """.trimMargin()
    )
  }
})

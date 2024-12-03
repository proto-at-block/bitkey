package build.wallet.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import build.wallet.db.DbQueryError
import build.wallet.sqldelight.ThrowingSqlDriver.QUERY_ERROR
import build.wallet.sqldelight.dummy.DummyDataEntity
import build.wallet.sqldelight.dummy.DummyDataEntityQueries
import build.wallet.sqldelight.dummy.DummyDatabase
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class FlowsTests : FunSpec({

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
    testDataQueries.clear()
  }

  context("asFlowOfList") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .asFlowOfList()
        .test {
          awaitItem().shouldBeOk(listOf(DummyDataEntity(id = 1, value = "chocolate")))
        }
    }

    test("many items - ok") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData()
        .asFlowOfList()
        .test {
          awaitItem().shouldBeOk(
            listOf(
              DummyDataEntity(1, "chocolate"),
              DummyDataEntity(2, "croissant")
            )
          )
        }
    }

    test("no items - ok") {
      testDataQueries.getDummyData()
        .asFlowOfList()
        .test {
          awaitItem().shouldBeOk(emptyList())
        }
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .asFlowOfList()
        .test {
          awaitItem().shouldBeErr(DbQueryError(QUERY_ERROR))
          awaitComplete() // Error is terminal
        }
    }
  }

  context("asFlowOfOneOrNull") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .asFlowOfOneOrNull()
        .test {
          awaitItem().shouldBeOk(DummyDataEntity(id = 1, value = "chocolate"))
        }
    }

    test("many items - error") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData()
        .asFlowOfOneOrNull()
        .test {
          awaitItem().shouldBeErr(
            DbQueryError(
              IllegalStateException("ResultSet returned more than 1 row for dummy:queryDummyData")
            )
          )
          awaitComplete() // Error is terminal
        }
    }

    test("no items - ok") {
      testDataQueries.getDummyData()
        .asFlowOfOneOrNull()
        .test {
          awaitItem().shouldBeOk(null)
        }
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .asFlowOfOneOrNull()
        .test {
          awaitItem().shouldBeErr(DbQueryError(QUERY_ERROR))
          awaitComplete() // Error is terminal
        }
    }
  }

  context("asFlowOfOne") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .asFlowOfOne()
        .test {
          awaitItem().shouldBeOk(DummyDataEntity(id = 1, value = "chocolate"))
        }
    }

    test("many items - error") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData()
        .asFlowOfOne()
        .test {
          awaitItem().shouldBeErr(
            DbQueryError(
              IllegalStateException("ResultSet returned more than 1 row for dummy:queryDummyData")
            )
          )
          awaitComplete() // Error is terminal
        }
    }

    test("no items - error") {
      testDataQueries.getDummyData()
        .asFlowOfOne()
        .test {
          awaitItem().shouldBeErr(
            DbQueryError(
              NullPointerException("ResultSet returned null for dummy:queryDummyData")
            )
          )
          awaitComplete() // Error is terminal
        }
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .asFlowOfOne()
        .test {
          awaitItem().shouldBeErr(DbQueryError(QUERY_ERROR))
          awaitComplete() // Error is terminal
        }
    }
  }
})

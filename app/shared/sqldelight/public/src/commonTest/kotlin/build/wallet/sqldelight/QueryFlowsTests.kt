package build.wallet.sqldelight

import app.cash.sqldelight.db.SqlDriver
import build.wallet.db.DbQueryError
import build.wallet.sqldelight.ThrowingSqlDriver.QUERY_ERROR
import build.wallet.sqldelight.dummy.DummyDataEntity
import build.wallet.sqldelight.dummy.DummyDataEntityQueries
import build.wallet.sqldelight.dummy.DummyDatabase
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class QueryFlowsTests : FunSpec({

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

  context("awaitAsListResult") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .awaitAsListResult()
        .shouldBeOk(listOf(DummyDataEntity(id = 1, value = "chocolate")))
    }

    test("many items - ok") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData()
        .awaitAsListResult()
        .shouldBeOk(
          listOf(
            DummyDataEntity(1, "chocolate"),
            DummyDataEntity(2, "croissant")
          )
        )
    }

    test("no items - ok") {
      testDataQueries.getDummyData().awaitAsListResult()
        .shouldBeOk(emptyList())
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .awaitAsListResult()
        .shouldBeErr(DbQueryError(QUERY_ERROR))
    }
  }

  context("awaitAsOneResult") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .awaitAsOneResult()
        .shouldBeOk(DummyDataEntity(id = 1, value = "chocolate"))
    }

    test("many items - error") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData()
        .awaitAsOneResult()
        .shouldBeErr(
          DbQueryError(
            IllegalStateException("ResultSet returned more than 1 row for dummy:queryDummyData")
          )
        )
    }

    test("no items - error") {
      testDataQueries.getDummyData()
        .awaitAsOneResult()
        .shouldBeErr(
          DbQueryError(
            NullPointerException("ResultSet returned null for dummy:queryDummyData")
          )
        )
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .awaitAsOneResult()
        .shouldBeErr(DbQueryError(QUERY_ERROR))
    }
  }

  context("awaitAsOneOrNullResult") {
    test("single item - ok") {
      testDataQueries.insertDummyData(1, "chocolate")

      testDataQueries.getDummyData()
        .awaitAsOneOrNullResult()
        .shouldBeOk(DummyDataEntity(id = 1, value = "chocolate"))
    }

    test("many items - error") {
      testDataQueries.insertDummyData(1, "chocolate")
      testDataQueries.insertDummyData(2, "croissant")

      testDataQueries.getDummyData().awaitAsOneOrNullResult()
        .shouldBeErr(
          DbQueryError(
            IllegalStateException("ResultSet returned more than 1 row for dummy:queryDummyData")
          )
        )
    }

    test("no items - ok") {
      testDataQueries.getDummyData()
        .awaitAsOneOrNullResult()
        .shouldBeOk(null)
    }

    test("query error") {
      testDataQueries.getDummyData(throwError = true)
        .awaitAsOneOrNullResult()
        .shouldBeErr(DbQueryError(QUERY_ERROR))
    }
  }
})

package build.wallet.fwup

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FwupDataDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()
  val dao = FwupDataDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory))

  test("fwup data flow") {
    val fwupData1 = FwupDataMock
    val fwupData2 = FwupDataMock.copy(version = "fake-2")

    dao.fwupData().test {
      awaitItem().shouldBe(Ok(null))

      dao.setFwupData(fwupData1)
      awaitItem().component1()
        .shouldNotBeNull().shouldBe(fwupData1)

      dao.setFwupData(fwupData2)
      awaitItem().component1()
        .shouldNotBeNull().shouldBe(fwupData2)

      dao.clear()
      awaitItem().shouldBe(Ok(null))
    }
  }
})

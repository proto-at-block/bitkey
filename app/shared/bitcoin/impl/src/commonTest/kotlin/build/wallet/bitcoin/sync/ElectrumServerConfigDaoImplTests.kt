package build.wallet.bitcoin.sync

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ElectrumServerConfigDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: ElectrumServerConfigRepository

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = ElectrumServerConfigRepositoryImpl(databaseProvider)
  }

  context("test F8e defined server storage") {
    val f8eElectrumServerDetails1 =
      ElectrumServerDetails(
        host = "one.info",
        port = "50002"
      )
    val f8eElectrumServerDetails2 =
      ElectrumServerDetails(
        host = "two.info",
        port = "50002"
      )

    test("store and retrieve F8e defined server") {
      dao.getF8eDefinedElectrumServer().test {
        awaitItem().shouldBeNull()
        dao.storeF8eDefinedElectrumConfig(f8eElectrumServerDetails1)
        awaitItem().shouldBe(ElectrumServer.F8eDefined(f8eElectrumServerDetails1))
      }
    }

    test("new f8e defined server overrides the old one") {
      dao.getF8eDefinedElectrumServer().test {
        awaitItem().shouldBeNull()

        dao.storeF8eDefinedElectrumConfig(f8eElectrumServerDetails1)
        awaitItem().shouldBe(ElectrumServer.F8eDefined(f8eElectrumServerDetails1))

        dao.storeF8eDefinedElectrumConfig(f8eElectrumServerDetails2)
        awaitItem().shouldBe(ElectrumServer.F8eDefined(f8eElectrumServerDetails2))
      }
    }
  }

  context("test user Electrum server preference storage") {
    val electrumServerPreference1 =
      ElectrumServerPreferenceValue.On(
        server = CustomElectrumServerMock
      )
    val electrumServerPreference2 =
      ElectrumServerPreferenceValue.Off(
        previousUserDefinedElectrumServer =
          CustomElectrumServerMock.copy(
            electrumServerDetails =
              ElectrumServerDetails(
                host = "two.info",
                port = "50002"
              )
          )
      )

    test("store and retrieve Electrum server preference") {
      dao.getUserElectrumServerPreference().test {
        awaitItem().shouldBeNull()

        dao.storeUserPreference(electrumServerPreference1)
        awaitItem().shouldBe(electrumServerPreference1)
      }
    }

    test("new Electrum server preference overrides existing one") {
      dao.getUserElectrumServerPreference().test {
        awaitItem().shouldBeNull()

        dao.storeUserPreference(electrumServerPreference1)
        awaitItem().shouldBe(electrumServerPreference1)

        dao.storeUserPreference(electrumServerPreference2)
        awaitItem().shouldBe(electrumServerPreference2)
      }
    }
  }
})

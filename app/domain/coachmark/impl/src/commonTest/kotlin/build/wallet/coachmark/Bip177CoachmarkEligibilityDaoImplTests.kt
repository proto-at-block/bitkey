package build.wallet.coachmark

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class Bip177CoachmarkEligibilityDaoImplTests :
  FunSpec({
    val sqlDriver = inMemorySqlDriver()
    lateinit var dao: Bip177CoachmarkEligibilityDao

    beforeTest {
      val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
      dao = Bip177CoachmarkEligibilityDaoImpl(databaseProvider)
    }

    test("defaults to null until recorded") {
      dao.getEligibility().value.shouldBe(null)
    }

    test("set → get round trip") {
      dao.setEligibility(true)
      dao.getEligibility().value.shouldBe(true)
    }

    test("value can be overwritten") {
      dao.setEligibility(true)
      dao.setEligibility(false)
      dao.getEligibility().value.shouldBe(false)
    }
  })

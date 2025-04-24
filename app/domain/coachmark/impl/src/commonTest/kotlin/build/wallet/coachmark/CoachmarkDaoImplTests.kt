package build.wallet.coachmark

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkDaoImplTests :
  FunSpec({
    val sqlDriver = inMemorySqlDriver()

    lateinit var dao: CoachmarkDao

    beforeTest {
      val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
      dao = CoachmarkDaoImpl(
        databaseProvider
      )
    }

    suspend fun createCoachmark() {
      dao.insertCoachmark(CoachmarkIdentifier.InheritanceCoachmark, Instant.DISTANT_FUTURE)
    }

    test("setViewed") {
      createCoachmark()
      dao
        .getCoachmark(CoachmarkIdentifier.InheritanceCoachmark)
        .value
        ?.viewed
        .shouldBe(false)
      dao.setViewed(CoachmarkIdentifier.InheritanceCoachmark)
      dao
        .getCoachmark(CoachmarkIdentifier.InheritanceCoachmark)
        .value
        ?.viewed
        .shouldBe(true)
    }

    test("getAllCoachmarks") {
      dao
        .getAllCoachmarks()
        .value
        .isEmpty()
        .shouldBe(true)
      createCoachmark()
      val list = dao.getAllCoachmarks()
      list.value.isNotEmpty().shouldBe(true)
      val inheritance = list.value[0]
      inheritance.id.shouldBe(CoachmarkIdentifier.InheritanceCoachmark)
      inheritance.viewed.shouldBe(false)
      inheritance.expiration.shouldBe(Instant.DISTANT_FUTURE)
    }

    test("resetCoachmarks") {
      createCoachmark()
      dao.resetCoachmarks()
      dao
        .getAllCoachmarks()
        .value
        .isEmpty()
        .shouldBe(true)
    }
  })

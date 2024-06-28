package build.wallet.coachmark

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
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
        databaseProvider,
        ClockFake()
      )
    }

    suspend fun createCoachmark() {
      dao.insertCoachmark(CoachmarkIdentifier.HiddenBalanceCoachmark, Instant.DISTANT_FUTURE)
    }

    test("setViewed") {
      createCoachmark()
      dao
        .getCoachmark(CoachmarkIdentifier.HiddenBalanceCoachmark)
        .value
        ?.viewed
        .shouldBe(false)
      dao.setViewed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      dao
        .getCoachmark(CoachmarkIdentifier.HiddenBalanceCoachmark)
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
      val hiddenBalance = list.value[0]
      hiddenBalance.coachmarkId.shouldBe(CoachmarkIdentifier.HiddenBalanceCoachmark.string)
      hiddenBalance.viewed.shouldBe(false)
      hiddenBalance.expiration.shouldBe(Instant.DISTANT_FUTURE)
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

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
      dao.insertCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark, Instant.DISTANT_FUTURE)
    }

    test("setViewed") {
      createCoachmark()
      dao
        .getCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .value
        ?.viewed
        .shouldBe(false)
      dao.setViewed(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
      dao
        .getCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
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
      val privateWallet = list.value[0]
      privateWallet.id.shouldBe(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
      privateWallet.viewed.shouldBe(false)
      privateWallet.expiration.shouldBe(Instant.DISTANT_FUTURE)
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

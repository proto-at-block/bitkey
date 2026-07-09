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

    test("insertCoachmark persists stable coachmark id") {
      createCoachmark()

      val storedId = sqlDriver.factory.sqlDriver!!
        .executeQuery(
          identifier = null,
          sql = "SELECT id FROM coachmarkEntity",
          mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
              if (cursor.next().value) cursor.getString(0) else null
            )
          },
          parameters = 0
        ).value

      storedId.shouldBe(CoachmarkIdentifier.PrivateWalletHomeCoachmark.id)
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

    test("getAllCoachmarks ignores unknown persisted ids") {
      dao.getAllCoachmarks()
      sqlDriver.factory.sqlDriver!!
        .execute(
          null,
          """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('totally_removed_coachmark', 0, NULL)
          """.trimIndent(),
          0
        )

      dao.getAllCoachmarks().value.shouldBe(emptyList())
    }

    test("getAllCoachmarks deletes unknown persisted rows") {
      dao.getAllCoachmarks()
      val driver = sqlDriver.factory.sqlDriver!!
      driver.execute(
        null,
        """
        INSERT INTO coachmarkEntity(id, viewed, expiration)
        VALUES ('totally_removed_coachmark', 0, NULL)
        """.trimIndent(),
        0
      )

      // First read prunes the orphan row.
      dao.getAllCoachmarks().value.shouldBe(emptyList())

      // Verify the row is actually gone from the table, not just filtered.
      val cursor = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM coachmarkEntity WHERE id = 'totally_removed_coachmark'",
        mapper = { c ->
          app.cash.sqldelight.db.QueryResult.Value(
            if (c.next().value) c.getLong(0) ?: 0L else 0L
          )
        },
        parameters = 0
      ).value
      cursor.shouldBe(0L)
    }

    test("getAllCoachmarks prunes legacy enum-name rows that were not normalized by migration") {
      dao.getAllCoachmarks()
      sqlDriver.factory.sqlDriver!!
        .execute(
          null,
          """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('PrivateWalletHomeCoachmark', 0, NULL)
          """.trimIndent(),
          0
        )

      dao.getAllCoachmarks().value.shouldBe(emptyList())
    }

    test("legacy enum-name rows cannot be fetched directly without migration") {
      dao.getAllCoachmarks()
      sqlDriver.factory.sqlDriver!!
        .execute(
          null,
          """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('PrivateWalletHomeCoachmark', 0, NULL)
          """.trimIndent(),
          0
        )

      dao
        .getCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .value
        .shouldBe(null)
    }
  })

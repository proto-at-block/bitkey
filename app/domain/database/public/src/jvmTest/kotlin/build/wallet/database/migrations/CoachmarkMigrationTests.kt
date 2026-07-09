package build.wallet.database.migrations

import app.cash.sqldelight.db.QueryResult
import build.wallet.database.migrateDatabase
import build.wallet.database.usingDatabase
import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Tests that the coachmark ID column is renamed properly.
 */
class CoachmarkMigrationTests : FunSpec({
  test("Test Coachmark Table after migration") {
    usingDatabaseWithFixtures(10) {
      table("coachmarkEntity") {
        // ID Column Renamed:
        columnNames.shouldNotContain("coachmarkId")

        rowAt(0) {
          valueShouldBe("id", "coachmarkId-val")
          valueShouldBe("viewed", "1")
          valueShouldBe("expiration", "2")
        }
      }
    }
  }

  test("Migration 83 normalizes legacy coachmark enum-name rows to stable ids") {
    usingDatabase(82) {
      driver.execute(
        identifier = null,
        sql = """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('PrivateWalletHomeCoachmark', 0, NULL)
        """.trimIndent(),
        parameters = 0
      ).await()
      driver.execute(
        identifier = null,
        sql = """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('Bip177Coachmark', 1, NULL)
        """.trimIndent(),
        parameters = 0
      ).await()
      driver.execute(
        identifier = null,
        sql = """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('bip_177_coachmark', 0, 123)
        """.trimIndent(),
        parameters = 0
      ).await()
      driver.execute(
        identifier = null,
        sql = """
          INSERT INTO coachmarkEntity(id, viewed, expiration)
          VALUES ('W3UpgradeBlockerCoachmark', 0, NULL)
        """.trimIndent(),
        parameters = 0
      ).await()

      migrateDatabase(toVersion = 83, fromVersion = 82)

      table("coachmarkEntity") {
        rowValues["id"].orEmpty().shouldContainExactlyInAnyOrder(
          "private_wallet_home_coachmark",
          "bip_177_coachmark",
          "w3_upgrade_blocker_coachmark"
        )
      }

      driver.executeQuery(
        identifier = null,
        sql = "SELECT viewed, expiration FROM coachmarkEntity WHERE id = 'bip_177_coachmark'",
        mapper = { cursor ->
          QueryResult.Value(
            if (cursor.next().value) {
              cursor.getLong(0) to cursor.getLong(1)
            } else {
              null
            }
          )
        },
        parameters = 0
      ).value shouldBe (0L to 123L)

      driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM coachmarkEntity WHERE id IN ('PrivateWalletHomeCoachmark', 'Bip177Coachmark', 'W3UpgradeBlockerCoachmark')",
        mapper = { cursor -> QueryResult.Value(cursor.getLong(0)) },
        parameters = 0
      ).value shouldBe 0
    }
  }
})

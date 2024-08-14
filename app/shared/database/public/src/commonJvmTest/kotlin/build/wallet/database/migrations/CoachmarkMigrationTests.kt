package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain

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
})

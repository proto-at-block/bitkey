package build.wallet.database.migrations

import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.usingDatabaseWithFixtures
import build.wallet.sqldelight.databaseContents
import io.kotest.core.spec.style.FunSpec

class FullMigrationTest : FunSpec({
  test("Full Database Migration Test") {
    // Migrate to the latest version to test all migrations+fixtures.
    usingDatabaseWithFixtures(BitkeyDatabase.Schema.version) {
      driver.databaseContents().tables.isNotEmpty()
    }
  }
})

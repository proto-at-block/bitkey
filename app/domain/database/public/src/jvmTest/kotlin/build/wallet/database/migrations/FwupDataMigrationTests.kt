package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain

class FwupDataMigrationTests : FunSpec({
  test("Migration 63 migrates fwupDataEntity to multi-MCU format") {
    usingDatabaseWithFixtures(64) {
      // Verify the table schema was updated correctly
      table("fwupDataEntity") {
        // The table should now have mcuRole as primary key instead of rowId
        columnNames.shouldNotContain("rowId")
        columnNames.shouldNotContain("currentSequenceId")
        columnNames.shouldContain("mcuRole")
        columnNames.shouldContain("mcuName")

        val mcuRoles = rowValues["mcuRole"].orEmpty()
        mcuRoles.shouldContainOnly("CORE")
      }

      // Verify mcuFwupStateEntity has sequence IDs
      table("mcuFwupStateEntity") {
        columnNames.shouldContain("mcuRole")
        columnNames.shouldContain("currentSequenceId")

        val mcuRoles = rowValues["mcuRole"].orEmpty()
        mcuRoles.shouldContainAll("CORE", "UXC")
      }
    }
  }
})

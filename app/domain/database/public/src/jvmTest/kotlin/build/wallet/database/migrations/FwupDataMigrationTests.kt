package build.wallet.database.migrations

import build.wallet.database.migrateDatabase
import build.wallet.database.usingDatabase
import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain

class FwupDataMigrationTests : FunSpec({
  test("Migration 86 discards cached firmware that predates device-change invalidation") {
    usingDatabase(86) {
      driver.execute(
        null,
        """
        INSERT INTO fwupDataEntity(mcuRole, mcuName, version, chunkSize, signatureOffset,
        appPropertiesOffset, firmware, signature, fwupMode)
        VALUES ('CORE', 'EFR32', '1.2.14', 1, 1, 1, x'00', x'00', 'Normal');
        """.trimIndent(),
        0
      ).await()
      driver.execute(
        null,
        "INSERT INTO mcuFwupStateEntity(mcuRole, currentSequenceId) VALUES ('CORE', 5);",
        0
      ).await()

      migrateDatabase(toVersion = 87, fromVersion = 86)

      // This cache cannot be attributed to a device, and assuming it belongs to whatever
      // hardware is currently paired is the bug being fixed. Also unsticks customers who
      // swapped hardware before installing this build.
      table("fwupDataEntity") {
        rowValues["mcuRole"].orEmpty().shouldBeEmpty()
      }
      table("mcuFwupStateEntity") {
        rowValues["mcuRole"].orEmpty().shouldBeEmpty()
      }
    }
  }

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

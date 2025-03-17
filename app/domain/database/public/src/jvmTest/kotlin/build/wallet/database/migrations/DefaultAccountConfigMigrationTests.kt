package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec

class DefaultAccountConfigMigrationTests : FunSpec({
  context("Migration 12: table templateFullAccountConfigEntity renamed to debugOptionsEntity") {
    test("Data survives migration after table rename from templateFullAccountConfigEntity") {
      usingDatabaseWithFixtures(12) {
        tableShouldNotExist("templateFullAccountConfigEntity")

        table("debugOptionsEntity") {
          rowAt(0) {
            valueShouldBe("rowId", "1")
            valueShouldBe("bitcoinNetworkType", "bitcoinNetworkType-val")
            valueShouldBe("fakeHardware", "1")
            valueShouldBe("f8eEnvironment", "f8eEnvironment-val")
            valueShouldBe("isTestAccount", "1")
            valueShouldBe("isUsingSocRecFakes", "1")
            valueShouldBe("delayNotifyDuration", "delayNotifyDuration-val")
          }
        }
      }
    }
  }

  context("Migration 13: Add skipCloudBackupOnboarding and skipNotificationsOnboarding to debugOptionsEntity") {
    test("Data survives migration, and new columns are added with default values") {
      usingDatabaseWithFixtures(13) {
        table("debugOptionsEntity") {
          rowAt(0) {
            valueShouldBe("rowId", "1")
            valueShouldBe("bitcoinNetworkType", "bitcoinNetworkType-val")
            valueShouldBe("fakeHardware", "1")
            valueShouldBe("f8eEnvironment", "f8eEnvironment-val")
            valueShouldBe("isTestAccount", "1")
            valueShouldBe("isUsingSocRecFakes", "1")
            valueShouldBe("delayNotifyDuration", "delayNotifyDuration-val")

            // Set to false by default
            valueShouldBe("skipCloudBackupOnboarding", "0")
            valueShouldBe("skipNotificationsOnboarding", "0")
          }
        }
      }
    }
  }
})

package build.wallet.database.migrations

import app.cash.sqldelight.db.QueryResult
import build.wallet.database.migrateDatabase
import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Tests migration 43 which fixes multiple active keysets issue by ensuring
 * only the newest keyset (by server ID ULID) is active per account.
 *
 * The issue: After migration 39, it's possible to have multiple active keysets
 * per keybox, which causes fullAccountView to return multiple rows for the same account.
 * Migration 43 should fix this by ensuring only the newest keyset is active.
 */
class MultipleActiveKeysetMigrationTests : FunSpec({
  test("Migration 43 fixes multiple active keysets per account") {
    // Step 1: Start with database version 39 (before migration 39 adds isActive columns)
    usingDatabaseWithFixtures(39) {
      // Insert our own test data at version 39 (before isActive columns exist)
      // At version 39, fullAccountEntity has keyboxId and keyboxEntity has active* references
      driver.execute(null, "DELETE FROM fullAccountEntity WHERE 1=1", 0)
      driver.execute(null, "DELETE FROM keyboxEntity WHERE 1=1", 0)
      driver.execute(null, "DELETE FROM spendingKeysetEntity WHERE 1=1", 0)
      driver.execute(null, "DELETE FROM appKeyBundleEntity WHERE 1=1", 0)
      driver.execute(null, "DELETE FROM hwKeyBundleEntity WHERE 1=1", 0)

      driver.execute(
        null,
        """
        INSERT INTO fullAccountEntity(accountId, keyboxId) 
        VALUES ('test-account-1', 'keybox-1')
        """.trimIndent(),
        0
      )

      driver.execute(
        null,
        """
        INSERT INTO keyboxEntity(
          id, account, activeSpendingKeysetId, activeKeyBundleId, activeHwKeyBundleId,
          networkType, fakeHardware, f8eEnvironment, isTestAccount, isUsingSocRecFakes,
          delayNotifyDuration, appGlobalAuthKeyHwSignature
        ) VALUES (
          'keybox-1', 'test-account-1', 'keyset-1', 'app-bundle-1', 'hw-bundle-1',
          'BITCOIN', 0, 'Development', 0, 0, 'PT0S', 'signature-1'
        )
        """.trimIndent(),
        0
      )

      // Create a second keybox (inactive at version 39)
      driver.execute(
        null,
        """
        INSERT INTO keyboxEntity(
          id, account, activeSpendingKeysetId, activeKeyBundleId, activeHwKeyBundleId,
          networkType, fakeHardware, f8eEnvironment, isTestAccount, isUsingSocRecFakes,
          delayNotifyDuration, appGlobalAuthKeyHwSignature
        ) VALUES (
          'keybox-2', 'test-account-1', 'keyset-2', 'app-bundle-2', 'hw-bundle-2',
          'BITCOIN', 0, 'Development', 0, 0, 'PT0S', 'signature-2'
        )
        """.trimIndent(),
        0
      )

      // Insert keysets for both keyboxes (no isActive column yet at version 39)
      driver.execute(
        null,
        """
        INSERT INTO spendingKeysetEntity(id, serverId, appKey, hardwareKey, serverKey)
        VALUES ('keyset-1', '01HZZZZZZZZZZZZZZZZZZZZZZ0', 'app-key-1', 'hw-key-1', 'server-key-1')
        """.trimIndent(),
        0
      )

      driver.execute(
        null,
        """
        INSERT INTO spendingKeysetEntity(id, serverId, appKey, hardwareKey, serverKey)
        VALUES ('keyset-2', '01HZZZZZZZZZZZZZZZZZZZZZZ1', 'app-key-2', 'hw-key-2', 'server-key-2')
        """.trimIndent(),
        0
      )

      // Insert app key bundles for both keyboxes
      driver.execute(
        null,
        """
        INSERT INTO appKeyBundleEntity(id, globalAuthKey, spendingKey, recoveryAuthKey)
        VALUES ('app-bundle-1', 'global-auth-1', 'app-spending-1', 'recovery-auth-1')
        """.trimIndent(),
        0
      )

      driver.execute(
        null,
        """
        INSERT INTO appKeyBundleEntity(id, globalAuthKey, spendingKey, recoveryAuthKey)
        VALUES ('app-bundle-2', 'global-auth-2', 'app-spending-2', 'recovery-auth-2')
        """.trimIndent(),
        0
      )

      // Insert hw key bundles for both keyboxes
      driver.execute(
        null,
        """
        INSERT INTO hwKeyBundleEntity(id, spendingKey, authKey)
        VALUES ('hw-bundle-1', 'hw-spending-1', 'hw-auth-1')
        """.trimIndent(),
        0
      )

      driver.execute(
        null,
        """
        INSERT INTO hwKeyBundleEntity(id, spendingKey, authKey)
        VALUES ('hw-bundle-2', 'hw-spending-2', 'hw-auth-2')
        """.trimIndent(),
        0
      )

      // Step 2: Run migration 39 to add isActive columns (database version 40)
      migrateDatabase(toVersion = 40, fromVersion = 39)

      // After migration 39, we should have isActive columns and multiple active keysets
      // This is because both keyboxes have keysets, and migration 39 marks them all as active
      table("spendingKeysetEntity") {
        columnNames.shouldContain("isActive")
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 2
      }

      table("appKeyBundleEntity") {
        columnNames.shouldContain("isActive")
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 2
      }

      table("hwKeyBundleEntity") {
        columnNames.shouldContain("isActive")
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 2
      }

      // The bug: fullAccountView now has 2 rows for the same account!
      val res = driver.executeQuery(
        null,
        "SELECT count(*) FROM fullAccountView WHERE accountId = 'test-account-1'",
        { cursor -> QueryResult.Value(cursor.getLong(0)) },
        0
      )

      res.value shouldBe 2

      // Step 3: Migrate to version 43 to set up the bug scenario (migrations 40-43)
      migrateDatabase(toVersion = 44, fromVersion = 40)

      // After migration 43, should have only 1 active keyset (the newest by server ID)
      table("spendingKeysetEntity") {
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 1

        // Verify it's the correct keyset (the one with newest ULID: 01HZZZZZZZZZZZZZZZZZZZZZZ1)
        val isActiveValues = rowValues["isActive"].orEmpty()
        val serverIdValues = rowValues["serverId"].orEmpty()
        val activeIndex = isActiveValues.indexOfFirst { it == "1" }
        if (activeIndex >= 0) {
          serverIdValues[activeIndex] shouldBe "01HZZZZZZZZZZZZZZZZZZZZZZ1"
        }
      }

      table("appKeyBundleEntity") {
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 1
      }

      table("hwKeyBundleEntity") {
        val activeCount = rowValues["isActive"]?.count { it == "1" } ?: 0
        activeCount shouldBe 1
      }

      // fullAccountView should now have only 1 row
      driver.executeQuery(
        null,
        "SELECT count(*) FROM fullAccountView WHERE accountId = 'test-account-1'",
        { cursor -> QueryResult.Value(cursor.getLong(0)) },
        0
      ).value shouldBe 1

      // Verify the other keysets are now marked as inactive
      table("spendingKeysetEntity") {
        val inactiveCount = rowValues["isActive"]?.count { it == "0" } ?: 0
        inactiveCount shouldBe 1
      }

      table("appKeyBundleEntity") {
        val inactiveCount = rowValues["isActive"]?.count { it == "0" } ?: 0
        inactiveCount shouldBe 1
      }

      table("hwKeyBundleEntity") {
        val inactiveCount = rowValues["isActive"]?.count { it == "0" } ?: 0
        inactiveCount shouldBe 1
      }
    }
  }
})

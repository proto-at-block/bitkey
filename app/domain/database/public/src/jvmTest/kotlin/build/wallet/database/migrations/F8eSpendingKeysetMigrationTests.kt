package build.wallet.database.migrations

import app.cash.sqldelight.db.QueryResult
import build.wallet.database.adapters.bitkey.F8eSpendingKeysetColumnAdapter
import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.collections.buildList

/**
 * Verifies migration 50 rewrites server spending keys into JSON-encoded F8eSpendingKeyset payloads
 * and maintains the expected schema guarantees.
 */
class F8eSpendingKeysetMigrationTests : FunSpec({
  val serverKey = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2Userverdpub/*"

  test("Migration 50 rewrites server keys to F8eSpendingKeyset JSON blobs") {
    // Run fixtures through schema version 51, which ensures migration 50 has been applied.
    usingDatabaseWithFixtures(51) {
      table("privateWalletMigrationEntity") {
        columnNames.shouldContain("newServerKey")

        rowAt(0) {
          valueShouldBe(
            "newServerKey",
            """{"keysetId":"fake-server-id","spendingPublicKey":"$serverKey"}"""
          )
          valueShouldBe("backupCompleted", "1")
        }
      }

      table("spendingKeysetEntity") {
        columnNames.shouldContain("serverKey")

        rowAt(0) {
          valueShouldBe(
            "serverKey",
            """{"keysetId":"serverId-val","spendingPublicKey":"$serverKey"}"""
          )
          valueShouldBe("isActive", "1")
        }

        // Ensure the unique index on active keysets is still enforced.
        shouldThrowAny {
          driver.execute(
            null,
            """
            INSERT INTO spendingKeysetEntity(
              id,
              keyboxId,
              appKey,
              hardwareKey,
              serverKey,
              isActive
            ) VALUES (
              'duplicate-active-keyset',
              'referenced-keyboxEntity-id-val',
              'dup-app-key',
              'dup-hw-key',
              '{"keysetId":"dup","spendingPublicKey":"dup"}',
              1
            )
            """.trimIndent(),
            0
          )
        }
      }

      table("recoverySpendingKeysetEntity") {
        columnNames.shouldContain("serverKey")

        rowAt(0) {
          valueShouldBe(
            "serverKey",
            """{"keysetId":"recovery-keyset-server-id-val","spendingPublicKey":"$serverKey"}"""
          )
          valueShouldBe("networkType", "SIGNET")
        }
      }

      table("localRecoveryAttemptEntity") {
        columnNames.shouldContain("serverSpendingKey")

        rowAt(0) {
          valueShouldBe(
            "serverSpendingKey",
            """{"keysetId":"serverKeysetId-val","spendingPublicKey":"$serverKey"}"""
          )
          valueShouldBe("backedUpToCloud", "1")
        }
      }

      // fullAccountView should expose the migrated JSON server key with no duplicate rows created.
      val fullAccountRows = driver.executeQuery(
        null,
        """
        SELECT accountId, serverKey
        FROM fullAccountView
        """.trimIndent(),
        { cursor ->
          QueryResult.Value(
            buildList {
              while (cursor.next().value) {
                add(
                  cursor.getString(0).orEmpty() to cursor.getString(1).shouldNotBeNull()
                )
              }
            }
          )
        },
        0
      ).value

      fullAccountRows.size shouldBe 1
      fullAccountRows.first().second shouldBe """{"keysetId":"serverId-val","spendingPublicKey":"$serverKey"}"""

      val f8eSpendingKeyset = F8eSpendingKeysetColumnAdapter.decode(fullAccountRows.first().second)
      f8eSpendingKeyset.keysetId shouldBe "serverId-val"
      f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe serverKey
    }
  }
})

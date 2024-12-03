package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class PartnershipMigrationTests : FunSpec({
  test("partnershipTransactionEntity: migrate timestamps from epoch milliseconds to ISO-8601") {
    usingDatabaseWithFixtures(21) {
      tableAtRow("partnershipTransactionEntity", 0) {
        val createdIso8601 = Instant.fromEpochMilliseconds(1731695850370).toString()
        valueShouldBe("created", createdIso8601)
        val updatedIso8601 = Instant.fromEpochMilliseconds(1731695850371).toString()
        valueShouldBe("updated", updatedIso8601)
      }
    }
  }
})

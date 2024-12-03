package build.wallet.database.migrations

import build.wallet.database.usingDatabaseWithFixtures
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class TransactionDetailMigrationTests : FunSpec({
  test("transactionDetailEntity: migrate timestamps from epoch milliseconds to ISO-8601") {
    usingDatabaseWithFixtures(22) {
      tableAtRow("transactionDetailEntity", 0) {
        val broadcastTime = Instant.fromEpochMilliseconds(1731695850370).toString()
        valueShouldBe("broadcastTime", broadcastTime)
        val estimatedConfirmationTime = Instant.fromEpochMilliseconds(1731696450370).toString()
        valueShouldBe("estimatedConfirmationTime", estimatedConfirmationTime)
      }
    }
  }
})

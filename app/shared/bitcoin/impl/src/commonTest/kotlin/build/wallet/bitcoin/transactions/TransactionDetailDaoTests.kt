package build.wallet.bitcoin.transactions

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TransactionDetailDaoTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  val broadcastTime = someInstant
  val confirmationTime = someInstant + 10.toDuration(DurationUnit.MINUTES)
  val transactionId = "fake-transaction-id"

  lateinit var dao: TransactionDetailDaoImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = TransactionDetailDaoImpl(databaseProvider)
  }

  test("insert and retrieve broadcast time for transaction") {
    dao.broadcastTimeForTransaction(transactionId).shouldBeNull()
    dao.insert(broadcastTime, transactionId, confirmationTime)
    dao.broadcastTimeForTransaction(transactionId).shouldBe(broadcastTime)
    dao.confirmationTimeForTransaction(transactionId).shouldBe(confirmationTime)
  }

  test("clear dao") {
    dao.insert(broadcastTime, transactionId, confirmationTime)
    dao.broadcastTimeForTransaction(transactionId).shouldNotBeNull()
    dao.clear()
    dao.broadcastTimeForTransaction(transactionId).shouldBeNull()
  }
})

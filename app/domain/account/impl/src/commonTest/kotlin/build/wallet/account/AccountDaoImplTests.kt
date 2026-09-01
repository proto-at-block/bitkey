package build.wallet.account

import build.wallet.bitcoin.metadata.WalletMetadataAccountId
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class AccountDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var databaseProvider: BitkeyDatabaseProviderImpl
  lateinit var dao: AccountDao

  beforeTest {
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = AccountDaoImpl(databaseProvider)
  }

  test("clear removes wallet metadata") {
    val database = databaseProvider.database()

    database.walletMetadataQueries.upsertTransactionNote(
      accountId = metadataAccountId,
      transactionId = transactionId,
      note = "Coffee",
      createdAt = instant(1),
      updatedAt = instant(2)
    )
    database.walletMetadataQueries.upsertDeletedTransactionNote(
      accountId = metadataAccountId,
      transactionId = deletedTransactionId,
      deletedAt = instant(3)
    )

    database.walletMetadataQueries
      .selectTransactionNotesForAccount(metadataAccountId)
      .executeAsList()
      .size
      .shouldBe(1)
    database.walletMetadataQueries
      .selectDeletedTransactionNotesForAccount(metadataAccountId)
      .executeAsList()
      .size
      .shouldBe(1)

    dao.clear().shouldBeOk()

    database.walletMetadataQueries
      .selectTransactionNotesForAccount(metadataAccountId)
      .executeAsList()
      .shouldBeEmpty()
    database.walletMetadataQueries
      .selectDeletedTransactionNotesForAccount(metadataAccountId)
      .executeAsList()
      .shouldBeEmpty()
  }
})

private val metadataAccountId = WalletMetadataAccountId("account-1")
private val transactionId = BitcoinTransactionId("txid-1")
private val deletedTransactionId = BitcoinTransactionId("txid-2")

private fun instant(offset: Long) = Instant.fromEpochSeconds(1_700_000_000 + offset)

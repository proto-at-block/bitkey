package build.wallet.bitcoin.metadata

import app.cash.turbine.test
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class WalletMetadataDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: WalletMetadataDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = WalletMetadataDaoImpl(databaseProvider)
  }

  test("persists account-scoped transaction notes") {
    dao.upsertTransactionNote(account1, note1).shouldBeOk()
    dao.upsertTransactionNote(account2, note2).shouldBeOk()

    dao.transactionNotes(account1).test {
      awaitItem().shouldBeOk(listOf(note1))
    }
    dao.transactionNotes(account2).test {
      awaitItem().shouldBeOk(listOf(note2))
    }
  }

  test("emits note updates as they are written") {
    dao.transactionNotes(account1).test {
      awaitItem().shouldBeOk(emptyList())

      dao.upsertTransactionNote(account1, note1).shouldBeOk()
      awaitItem().shouldBeOk(listOf(note1))

      val updated = note1.copy(note = "Coffee again", updatedAt = instant(23))
      dao.upsertTransactionNote(account1, updated).shouldBeOk()
      awaitItem().shouldBeOk(listOf(updated))

      dao.deleteTransactionNote(account1, note1.transactionId, instant(30)).shouldBeOk()
      awaitItem().shouldBeOk(emptyList())
    }
  }

  test("deletes active notes records tombstones and recreating clears tombstones") {
    val deletedNote = WalletMetadataTombstone.DeletedTransactionNote(
      transactionId = note1.transactionId,
      deletedAt = instant(20)
    )
    val recreatedNote = note1.copy(note = "Coffee again", updatedAt = instant(23))

    dao.upsertTransactionNote(account1, note1).shouldBeOk()

    dao.deleteTransactionNote(account1, note1.transactionId, deletedNote.deletedAt).shouldBeOk()

    dao.transactionNote(account1, note1.transactionId).shouldBeOk(null)
    dao.tombstones(account1).shouldBeOk(setOf(deletedNote))

    dao.upsertTransactionNote(account1, recreatedNote).shouldBeOk()

    dao.transactionNote(account1, note1.transactionId).shouldBeOk(recreatedNote)
    dao.tombstones(account1).shouldBeOk(emptySet())
  }

  test("looks up a single transaction note by account and transaction id") {
    dao.transactionNote(account1, note1.transactionId).shouldBeOk(null)

    dao.upsertTransactionNote(account1, note1).shouldBeOk()
    dao.upsertTransactionNote(account2, note2).shouldBeOk()

    dao.transactionNote(account1, note1.transactionId).shouldBeOk(note1)
    // Scoped to the account: note2 belongs to account2.
    dao.transactionNote(account1, note2.transactionId).shouldBeOk(null)
    dao.transactionNote(account2, note2.transactionId).shouldBeOk(note2)

    dao.deleteTransactionNote(account1, note1.transactionId, instant(10)).shouldBeOk()
    dao.transactionNote(account1, note1.transactionId).shouldBeOk(null)
  }
})

private val account1 = WalletMetadataAccountId("account-1")
private val account2 = WalletMetadataAccountId("account-2")

private val note1 = transactionNote(
  transactionId = BitcoinTransactionId("txid-1"),
  note = "Coffee",
  createdAt = instant(1),
  updatedAt = instant(2)
)
private val note2 = transactionNote(
  transactionId = BitcoinTransactionId("txid-2"),
  note = "Rent",
  createdAt = instant(3),
  updatedAt = instant(4)
)

private fun transactionNote(
  transactionId: BitcoinTransactionId,
  note: String,
  createdAt: Instant,
  updatedAt: Instant,
) = TransactionNote(
  transactionId = transactionId,
  note = note,
  createdAt = createdAt,
  updatedAt = updatedAt
)

private fun instant(offset: Long) = Instant.fromEpochSeconds(1_700_000_000 + offset)

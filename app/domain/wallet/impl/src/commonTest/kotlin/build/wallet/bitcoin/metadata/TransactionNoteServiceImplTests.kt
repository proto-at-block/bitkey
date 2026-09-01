package build.wallet.bitcoin.metadata

import app.cash.turbine.test
import bitkey.recovery.WalletMetadataServerBackupServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class TransactionNoteServiceImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val clock = ClockFake(now = instant(0))
  val accountService = AccountServiceFake()
  val walletMetadataServerBackupService = WalletMetadataServerBackupServiceFake()

  lateinit var walletMetadataDao: WalletMetadataDao
  lateinit var service: TransactionNoteService

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    walletMetadataDao = WalletMetadataDaoImpl(databaseProvider)
    service = TransactionNoteServiceImpl(
      accountService = accountService,
      walletMetadataDao = walletMetadataDao,
      walletMetadataServerBackupService = walletMetadataServerBackupService,
      clock = clock
    )
    walletMetadataServerBackupService.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    clock.now = instant(0)
  }

  test("creates edits and observes transaction notes") {
    val created = service.createOrUpdateNote(
      transactionId = transactionId,
      note = " Coffee "
    ).shouldBeOk()

    created.shouldBe(
      TransactionNote(
        transactionId = transactionId,
        note = "Coffee",
        createdAt = instant(0),
        updatedAt = instant(0)
      )
    )
    service.notes().test {
      awaitItem().shouldBeOk(mapOf(transactionId to created))
    }

    clock.now = instant(1)
    val edited = service.createOrUpdateNote(
      transactionId = transactionId,
      note = "Coffee with Sam"
    ).shouldBeOk()

    edited.createdAt.shouldBe(instant(0))
    edited.updatedAt.shouldBe(instant(1))
    edited.note.shouldBe("Coffee with Sam")
    service.notes().test {
      awaitItem().shouldBeOk(mapOf(transactionId to edited))
    }
  }

  test("requests server backup after note edits and deletions") {
    val accountId = WalletMetadataAccountId.fromAccountId(FullAccountMock.accountId)

    service.createOrUpdateNote(
      transactionId = transactionId,
      note = "Coffee"
    ).shouldBeOk()
    walletMetadataServerBackupService.requestBackupCalls.shouldBe(listOf(accountId))

    service.deleteNote(transactionId).shouldBeOk()
    walletMetadataServerBackupService.requestBackupCalls.shouldBe(listOf(accountId, accountId))
  }

  test("note edit succeeds even when backup request fails") {
    walletMetadataServerBackupService.requestBackupResult = Err(Error("backup scheduling failed"))

    service.createOrUpdateNote(
      transactionId = transactionId,
      note = "Coffee"
    ).shouldBeOk()
  }

  test("emits note updates while observing") {
    service.notes().test {
      awaitItem().shouldBeOk(emptyMap())

      val created = service.createOrUpdateNote(transactionId, note = "Coffee").shouldBeOk()
      awaitItem().shouldBeOk(mapOf(transactionId to created))

      service.deleteNote(transactionId).shouldBeOk()
      awaitItem().shouldBeOk(emptyMap())
    }
  }

  test("rejects empty and oversized notes") {
    service.createOrUpdateNote(transactionId, note = " ")
      .shouldBeErrOfType<TransactionNoteServiceError.InvalidNote>()
      .message
      .shouldBe("Transaction note cannot be blank.")

    val oversizedNote = "a".repeat(TransactionNote.MAX_NOTE_LENGTH + 1)
    service.createOrUpdateNote(transactionId, note = oversizedNote)
      .shouldBeErrOfType<TransactionNoteServiceError.InvalidNote>()
      .message
      .shouldBe("Transaction note cannot exceed ${TransactionNote.MAX_NOTE_LENGTH} characters.")
  }

  test("deletes notes and records tombstones") {
    service.createOrUpdateNote(transactionId, note = "Coffee").shouldBeOk()

    clock.now = instant(2)
    service.deleteNote(transactionId).shouldBeOk()

    service.notes().test {
      awaitItem().shouldBeOk(emptyMap())
    }
    walletMetadataDao.tombstones(accountId).shouldBeOk(
      setOf(
        WalletMetadataTombstone.DeletedTransactionNote(
          transactionId = transactionId,
          deletedAt = instant(2)
        )
      )
    )
  }

  test("scopes notes to the active account") {
    service.createOrUpdateNote(transactionId, note = "Coffee").shouldBeOk()

    walletMetadataDao.transactionNote(accountId, transactionId)
      .shouldBeOk()
      .shouldBe(
        TransactionNote(
          transactionId = transactionId,
          note = "Coffee",
          createdAt = instant(0),
          updatedAt = instant(0)
        )
      )
  }

  test("returns NoActiveAccount error without an active account") {
    accountService.reset()

    service.createOrUpdateNote(transactionId, note = "Coffee")
      .shouldBeErrOfType<TransactionNoteServiceError.NoActiveAccount>()
    service.deleteNote(transactionId)
      .shouldBeErrOfType<TransactionNoteServiceError.NoActiveAccount>()
    service.notes().test {
      awaitItem().shouldBeOk(emptyMap())
    }
  }
})

private val accountId = WalletMetadataAccountId.fromAccountId(FullAccountMock.accountId)
private val transactionId = BitcoinTransactionId("txid-1")

private fun instant(offset: Long) = Instant.fromEpochSeconds(1_700_000_000 + offset)

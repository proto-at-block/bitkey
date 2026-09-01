package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.WalletMetadataBackup
import bitkey.f8e.account.WalletMetadataBackupF8eClientFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.bitcoin.metadata.WalletMetadataAccountId
import build.wallet.bitcoin.metadata.WalletMetadataDao
import build.wallet.bitcoin.metadata.WalletMetadataDaoImpl
import build.wallet.bitcoin.metadata.WalletMetadataSnapshot
import build.wallet.bitcoin.metadata.WalletMetadataTombstone
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.DoubleFlag
import build.wallet.feature.setFlagValue
import build.wallet.feature.flags.TransactionNotesFeatureFlag
import build.wallet.feature.flags.TransactionNotesSyncFrequencySecondsFeatureFlag
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.ktor.result.HttpError
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.encodeUtf8

class WalletMetadataServerBackupServiceImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var databaseProvider: BitkeyDatabaseProviderImpl
  lateinit var walletMetadataDao: WalletMetadataDao
  lateinit var accountService: AccountServiceFake
  lateinit var accountConfigService: AccountConfigServiceFake
  lateinit var f8eClient: WalletMetadataBackupF8eClientFake
  lateinit var listKeysetsF8eClient: ListKeysetsF8eClientMock
  lateinit var ssekDao: SsekDaoFake
  lateinit var storeFactory: EncryptedKeyValueStoreFactoryFake
  lateinit var symmetricKeyEncryptor: SymmetricKeyEncryptorFake
  lateinit var transactionNotesFeatureFlag: TransactionNotesFeatureFlag
  lateinit var transactionNotesSyncFrequencySecondsFeatureFlag:
    TransactionNotesSyncFrequencySecondsFeatureFlag
  lateinit var service: WalletMetadataServerBackupServiceImpl

  // FullAccountMock's server-derived metadata account id.
  val metadataAccountId = WalletMetadataAccountId.fromAccountId(FullAccountMock.accountId)
  val sealedSsek: SealedSsek = "sealed-ssek".encodeUtf8()
  val transactionId = BitcoinTransactionId("txid-1")

  beforeTest {
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    walletMetadataDao = WalletMetadataDaoImpl(databaseProvider)
    accountService = AccountServiceFake()
    accountConfigService = AccountConfigServiceFake()
    f8eClient = WalletMetadataBackupF8eClientFake()
    listKeysetsF8eClient = ListKeysetsF8eClientMock()
    ssekDao = SsekDaoFake()
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
    transactionNotesFeatureFlag = TransactionNotesFeatureFlag(FeatureFlagDaoFake())
    transactionNotesFeatureFlag.setFlagValue(true)
    transactionNotesSyncFrequencySecondsFeatureFlag =
      TransactionNotesSyncFrequencySecondsFeatureFlag(FeatureFlagDaoFake())

    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )

    // Wire up an active full account, a recorded SSEK, and a persisted note so an upload
    // has everything it needs to succeed.
    accountService.setActiveAccount(FullAccountMock)
    ssekDao.set(sealedSsek, Ssek(key = SymmetricKeyImpl(raw = "raw-ssek".encodeUtf8())))
    service.recordSealedSsek(metadataAccountId, sealedSsek).shouldBeOk()
    walletMetadataDao.upsertTransactionNote(
      accountId = metadataAccountId,
      note = TransactionNote(
        transactionId = transactionId,
        note = "Coffee",
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
      )
    ).shouldBeOk()
  }

  test("worker waits for foreground on startup and dynamically schedules periodic sync") {
    service.runStrategy.size.shouldBe(5)
    service.runStrategy.filterIsInstance<build.wallet.worker.RunStrategy.Startup>()
      .single().backgroundStrategy.shouldBe(build.wallet.worker.BackgroundStrategy.Wait)
    // Periodic tick skips in the background; event-driven triggers (flag enablement, account
    // activation, immediate edit requests) wait so their one-shot emissions are not dropped.
    val onEventStrategies = service.runStrategy
      .filterIsInstance<build.wallet.worker.RunStrategy.OnEvent>()
    onEventStrategies.size.shouldBe(4)
    onEventStrategies.count {
      it.backgroundStrategy == build.wallet.worker.BackgroundStrategy.Skip
    }.shouldBe(1)
    onEventStrategies.count {
      it.backgroundStrategy == build.wallet.worker.BackgroundStrategy.Wait
    }.shouldBe(3)
  }

  test("worker schedule is not frozen to the flag value at construction") {
    val schedules = service.runStrategy
      .filterIsInstance<build.wallet.worker.RunStrategy.OnEvent>()
    transactionNotesSyncFrequencySecondsFeatureFlag.setFlagValue(DoubleFlag(5.0))

    schedules.forEach { it.observer.shouldNotBeNull() }
    // The periodic observer reads the flag inside its loop; construction did not create a
    // Periodic with the old fifteen-minute default. Timing behavior is covered by
    // AppWorkerExecutor tests.
    service.runStrategy.filterIsInstance<build.wallet.worker.RunStrategy.Periodic>()
      .shouldBe(emptyList())
  }

  test("provision uploads an encrypted empty snapshot before the first note") {
    databaseProvider.database().walletMetadataQueries.clear()

    service.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeOk()

    f8eClient.putCalls.size.shouldBe(1)
    val uploaded = f8eClient.putCalls.single().second
    uploaded.sealedSsek.shouldBe(sealedSsek)
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, uploaded).shouldBeOk { snapshot ->
      snapshot.shouldNotBeNull().transactionNotes.shouldBe(emptySet())
    }
    uploaded.sealedWalletMetadataSnapshot.value.shouldContain("\"transactionNotes\":[]")
    uploaded.sealedWalletMetadataSnapshot.value.shouldContain("\"tombstones\":[]")
  }

  test("repeated empty provisioning converges without losing retry state") {
    databaseProvider.database().walletMetadataQueries.clear()

    service.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeOk()
    service.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeOk()

    f8eClient.putCalls.size.shouldBe(2)
    f8eClient.storedBackup.shouldNotBeNull().sealedSsek.shouldBe(sealedSsek)
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(2)
  }

  test("failed empty provisioning retains durable retry intent") {
    databaseProvider.database().walletMetadataQueries.clear()
    f8eClient.putResult = Err(HttpError.NetworkError(Throwable("offline")))

    service.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeErrOfType<Error>()

    f8eClient.putResult = Ok(Unit)
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(2)
  }


  test("failed SSEK rotation keeps the new target key for retry") {
    val oldSealedSsek = "old-sealed-ssek".encodeUtf8()
    val oldSsek = Ssek(key = SymmetricKeyImpl(raw = "old-raw-ssek".encodeUtf8()))
    val newSealedSsek = "new-sealed-ssek".encodeUtf8()
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("remote-tx"),
      note = "Remote",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(1)
    )
    databaseProvider.database().walletMetadataQueries.clear()
    walletMetadataDao.upsertTransactionNote(
      accountId = metadataAccountId,
      note = remoteNote
    ).shouldBeOk()
    ssekDao.set(oldSealedSsek, oldSsek)
    ssekDao.set(newSealedSsek, Ssek(key = SymmetricKeyImpl(raw = "new-raw-ssek".encodeUtf8())))
    f8eClient.storedBackup = WalletMetadataBackup(
      sealedSsek = oldSealedSsek,
      sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
        unsealedData = kotlinx.serialization.json.Json.encodeToString<RawWalletMetadataSnapshot>(
          WalletMetadataSnapshot(
            accountId = metadataAccountId,
            transactionNotes = setOf(remoteNote),
            updatedAt = remoteNote.updatedAt
          ).toRaw()
        ).encodeUtf8(),
        key = oldSsek.key,
        aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
      )
    )
    f8eClient.putResult = Err(HttpError.NetworkError(Throwable("offline")))

    service.provision(FullAccountMock.accountId, newSealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeErrOfType<Error>()

    f8eClient.putResult = Ok(Unit)
    service.syncPendingBackups().shouldBeOk()

    val retriedUpload = f8eClient.putCalls.last().second
    retriedUpload.sealedSsek.shouldBe(newSealedSsek)
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, retriedUpload).shouldBeOk { snapshot ->
      snapshot.shouldNotBeNull().transactionNotes
        .single { it.transactionId == remoteNote.transactionId }
        .note.shouldBe("Remote")
    }
  }

  test("requestBackup is a local-only write and does not touch the network") {
    // requestBackup must be safe to await inline on the note edit path: it only records
    // the durable pending marker. The actual upload happens via syncPendingBackups.
    service.requestBackup(metadataAccountId).shouldBeOk()

    f8eClient.putCalls.shouldBe(emptyList())
  }

  test("syncPendingBackups uploads pending work and clears the marker") {
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.map { it.first }.shouldBe(listOf(FullAccountMock.accountId))

    // Marker was cleared: a second run has nothing to do.
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(1)
  }


  test("sync uses the active full account environment") {
    accountConfigService.setF8eEnvironment(Production).shouldBeOk()
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = KeyboxMock.copy(
          config = KeyboxMock.config.copy(f8eEnvironment = Staging)
        )
      )
    )
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()

    f8eClient.getEnvironments.shouldBe(listOf(Staging))
    f8eClient.putEnvironments.shouldBe(listOf(Staging))
  }

  test("failed upload leaves the pending marker for the worker to retry") {
    f8eClient.putResult = Err(HttpError.NetworkError(Throwable("offline")))

    service.requestBackup(metadataAccountId).shouldBeOk()
    service.syncPendingBackups().shouldBeErrOfType<Error>()
    f8eClient.putCalls.size.shouldBe(1)

    // Marker remained: a later worker run (now succeeding) retries and uploads.
    f8eClient.putResult = Ok(Unit)
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(2)
  }

  fun sealedJson(json: String): WalletMetadataBackup = WalletMetadataBackup(
    sealedSsek = sealedSsek,
    sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
      unsealedData = json.encodeUtf8(),
      key = SymmetricKeyImpl(raw = "raw-ssek".encodeUtf8()),
      aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
    )
  )

  fun sealedBackup(snapshot: WalletMetadataSnapshot): WalletMetadataBackup =
    sealedJson(kotlinx.serialization.json.Json.encodeToString<RawWalletMetadataSnapshot>(snapshot.toRaw()))


  test("existing empty account backfill uploads encrypted empty snapshot") {
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    databaseProvider.database().walletMetadataQueries.clear()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeOk()

    f8eClient.putCalls.single().second.sealedWalletMetadataSnapshot.value
      .shouldContain("\"transactionNotes\":[]")
  }

  test("existing account backfill uses descriptor SSEK and uploads pending note") {
    // Simulate a fresh metadata service installation: no account-scoped metadata SSEK marker.
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeOk()

    f8eClient.putCalls.size.shouldBe(1)
    f8eClient.putCalls.single().second.sealedSsek.shouldBe(sealedSsek)
    f8eClient.putCalls.single().second.sealedWalletMetadataSnapshot.value
      .shouldContain("Coffee")
  }

  test("private descriptor backups without wrapped SSEK fail as invariant violation") {
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = null,
        descriptorBackups = listOf(
          DescriptorBackup(
            keysetId = "keyset",
            sealedDescriptor = build.wallet.encrypt.XCiphertext("descriptor"),
            privateWalletRootXpub = build.wallet.encrypt.XCiphertext("root")
          )
        ),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeErrOfType<Error>()
    service.remediation.value.shouldBe(WalletMetadataRemediation.None)
    f8eClient.putCalls.shouldBe(emptyList())
  }

  test("missing descriptor SSEK becomes terminal unsupported remediation") {
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = null,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.executeWork()

    service.remediation.value.shouldBeInstanceOf<WalletMetadataRemediation.Unsupported>()
    f8eClient.putCalls.shouldBe(emptyList())
  }

  test("descriptor source of truth replaces stale local metadata SSEK during backfill") {
    val staleSealedSsek = "stale-sealed-ssek".encodeUtf8()
    ssekDao.set(staleSealedSsek, Ssek(key = SymmetricKeyImpl("stale-key".encodeUtf8())))
    service.recordSealedSsek(metadataAccountId, staleSealedSsek).shouldBeOk()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeOk()

    f8eClient.putCalls.single().second.sealedSsek.shouldBe(sealedSsek)
  }

  test("existing account backfill fails closed when descriptor SSEK is unavailable locally") {
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    ssekDao.clear()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()
    service.remediation.value.shouldBe(
      WalletMetadataRemediation.HardwareUnsealRequired(
        accountId = FullAccountMock.accountId,
        sealedSsek = sealedSsek
      )
    )
    f8eClient.putCalls.shouldBe(emptyList())
  }

  test("fetchBackup uses the recovery-provided environment") {
    service.fetchBackup(
      accountConfigService.defaultConfig().value.f8eEnvironment,
      FullAccountMock.accountId
    ).shouldBeOk()

    f8eClient.getCalls shouldBe listOf(FullAccountMock.accountId)
  }

  test("fetchBackup is gated by the transaction notes flag") {
    transactionNotesFeatureFlag.setFlagValue(false)

    service.fetchBackup(
      accountConfigService.defaultConfig().value.f8eEnvironment,
      FullAccountMock.accountId
    ).shouldBeOk(null)

    f8eClient.getCalls shouldBe emptyList()
  }

  test("post-recovery missing local SSEK stops retry and foreground remediation restores notes") {
    databaseProvider.database().walletMetadataQueries.clear()
    val backup = sealedBackup(
      WalletMetadataSnapshot(
        accountId = metadataAccountId,
        transactionNotes = setOf(
          TransactionNote(
            transactionId = BitcoinTransactionId("remote-tx"),
            note = "Recovered note",
            createdAt = Instant.fromEpochMilliseconds(1),
            updatedAt = Instant.fromEpochMilliseconds(1)
          )
        ),
        updatedAt = Instant.fromEpochMilliseconds(1)
      )
    )
    f8eClient.getResult = Ok(backup)
    ssekDao.clear()

    service.executeWork()
    service.remediation.value.shouldBe(
      WalletMetadataRemediation.HardwareUnsealRequired(
        accountId = FullAccountMock.accountId,
        sealedSsek = sealedSsek
      )
    )

    service.remediateWithUnsealedSsek(
      FullAccountMock.accountId,
      sealedSsek,
      Ssek(key = SymmetricKeyImpl(raw = "raw-ssek".encodeUtf8()))
    ).shouldBeOk()

    walletMetadataDao.snapshot(metadataAccountId).shouldBeOk { snapshot ->
      snapshot.shouldNotBeNull().transactionNotes.single().note.shouldBe("Recovered note")
    }
    service.remediation.value.shouldBe(WalletMetadataRemediation.None)
  }

  test("recordUnsealedSsek enables restore on a fresh installation") {
    val backup = sealedBackup(
      WalletMetadataSnapshot(
        accountId = metadataAccountId,
        transactionNotes = setOf(
          TransactionNote(
            transactionId = BitcoinTransactionId("remote-tx"),
            note = "Remote",
            createdAt = Instant.fromEpochMilliseconds(1),
            updatedAt = Instant.fromEpochMilliseconds(1)
          )
        ),
        updatedAt = Instant.fromEpochMilliseconds(1)
      )
    )
    val restoredSsek = Ssek(key = SymmetricKeyImpl(raw = "raw-ssek".encodeUtf8()))

    service.recordUnsealedSsek(FullAccountMock.accountId, backup.sealedSsek, restoredSsek).shouldBeOk()
    service.hasSsek(backup.sealedSsek).shouldBeOk(true)
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, backup).shouldBeOk()
  }

  test("SSEK rotation preserves existing remote notes and tombstones under new key") {
    val oldSealedSsek = "old-sealed-ssek".encodeUtf8()
    val oldSsek = Ssek(key = SymmetricKeyImpl(raw = "old-raw-ssek".encodeUtf8()))
    val deletedId = BitcoinTransactionId("deleted-tx")
    ssekDao.set(oldSealedSsek, oldSsek)
    val remoteSnapshot = WalletMetadataSnapshot(
      accountId = metadataAccountId,
      transactionNotes = setOf(
        TransactionNote(
          transactionId = BitcoinTransactionId("remote-tx"),
          note = "Preserve me",
          createdAt = Instant.fromEpochMilliseconds(1),
          updatedAt = Instant.fromEpochMilliseconds(2)
        )
      ),
      tombstones = setOf(
        WalletMetadataTombstone.DeletedTransactionNote(
          transactionId = deletedId,
          deletedAt = Instant.fromEpochMilliseconds(3)
        )
      ),
      updatedAt = Instant.fromEpochMilliseconds(3)
    )
    f8eClient.storedBackup = WalletMetadataBackup(
      sealedSsek = oldSealedSsek,
      sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
        unsealedData = kotlinx.serialization.json.Json.encodeToString<RawWalletMetadataSnapshot>(remoteSnapshot.toRaw()).encodeUtf8(),
        key = oldSsek.key,
        aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
      )
    )

    service.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeOk()

    val rotated = f8eClient.storedBackup.shouldNotBeNull()
    rotated.sealedSsek.shouldBe(sealedSsek)
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, rotated).shouldBeOk { snapshot ->
      snapshot.shouldNotBeNull().transactionNotes
        .single { it.transactionId == BitcoinTransactionId("remote-tx") }
        .note.shouldBe("Preserve me")
      snapshot.tombstones.single()
        .shouldBeInstanceOf<WalletMetadataTombstone.DeletedTransactionNote>()
        .transactionId.shouldBe(deletedId)
    }
  }

  test("reinstall mid-rotation re-rotates the backup to the descriptor SSEK") {
    // A rotation to a new SSEK updated the descriptor backups, but the metadata PUT never
    // completed before the app was reinstalled: the local rotation marker is gone and the
    // remote metadata backup is still sealed with the old key.
    val oldSealedSsek = "old-sealed-ssek".encodeUtf8()
    val oldSsek = Ssek(key = SymmetricKeyImpl(raw = "old-raw-ssek".encodeUtf8()))
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("remote-tx"),
      note = "Sealed under the retired key",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(1)
    )
    databaseProvider.database().walletMetadataQueries.clear()
    // Fresh install: no recorded key, no pending rotation target; both plaintext keys are
    // available (the old key from remediation/restore, the new key from descriptor recovery).
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    ssekDao.set(oldSealedSsek, oldSsek)
    f8eClient.storedBackup = WalletMetadataBackup(
      sealedSsek = oldSealedSsek,
      sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
        unsealedData = kotlinx.serialization.json.Json.encodeToString<RawWalletMetadataSnapshot>(
          WalletMetadataSnapshot(
            accountId = metadataAccountId,
            transactionNotes = setOf(remoteNote),
            updatedAt = remoteNote.updatedAt
          ).toRaw()
        ).encodeUtf8(),
        key = oldSsek.key,
        aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
      )
    )
    // The descriptor backups (source of truth, sealed by the currently paired hardware) already
    // advertise the new key.
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeOk()

    // The remote notes were merged locally and the backup was re-encrypted under the
    // descriptor key, completing the interrupted rotation.
    walletMetadataDao.transactionNote(metadataAccountId, remoteNote.transactionId)
      .shouldBeOk(remoteNote)
    val reRotated = f8eClient.storedBackup.shouldNotBeNull()
    reRotated.sealedSsek.shouldBe(sealedSsek)
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, reRotated).shouldBeOk { snapshot ->
      snapshot.shouldNotBeNull().transactionNotes.single().note
        .shouldBe("Sealed under the retired key")
    }
    service.remediation.value.shouldBe(WalletMetadataRemediation.None)
  }

  test("reinstall mid-rotation without the old key targets the descriptor SSEK in remediation") {
    // Same interrupted rotation, but this installation never unsealed the old key: remediation
    // must ask hardware for the old key while targeting the descriptor key, so completing
    // remediation finishes the rotation instead of re-adopting the retired key.
    val oldSealedSsek = "old-sealed-ssek".encodeUtf8()
    val oldSsek = Ssek(key = SymmetricKeyImpl(raw = "old-raw-ssek".encodeUtf8()))
    databaseProvider.database().walletMetadataQueries.clear()
    storeFactory = EncryptedKeyValueStoreFactoryFake()
    service = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = walletMetadataDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = storeFactory,
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    ssekDao.clear()
    // Only the descriptor (new) key is available locally.
    ssekDao.set(sealedSsek, Ssek(key = SymmetricKeyImpl(raw = "raw-ssek".encodeUtf8())))
    f8eClient.storedBackup = WalletMetadataBackup(
      sealedSsek = oldSealedSsek,
      sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
        unsealedData = kotlinx.serialization.json.Json.encodeToString<RawWalletMetadataSnapshot>(
          WalletMetadataSnapshot(
            accountId = metadataAccountId,
            transactionNotes = setOf(
              TransactionNote(
                transactionId = BitcoinTransactionId("remote-tx"),
                note = "Old note",
                createdAt = Instant.fromEpochMilliseconds(1),
                updatedAt = Instant.fromEpochMilliseconds(1)
              )
            ),
            updatedAt = Instant.fromEpochMilliseconds(1)
          ).toRaw()
        ).encodeUtf8(),
        key = oldSsek.key,
        aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
      )
    )
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = sealedSsek,
        descriptorBackups = emptyList(),
        activeKeysetId = "active"
      )
    )

    service.syncPendingBackups().shouldBeOk()

    service.remediation.value.shouldBe(
      WalletMetadataRemediation.HardwareUnsealRequired(
        accountId = FullAccountMock.accountId,
        sealedSsek = oldSealedSsek,
        targetSealedSsek = sealedSsek
      )
    )
    f8eClient.putCalls.shouldBe(emptyList())

    // Hardware unseals the old key: notes restore and the rotation completes under the target.
    service.remediateWithUnsealedSsek(FullAccountMock.accountId, oldSealedSsek, oldSsek)
      .shouldBeOk()
    walletMetadataDao.transactionNote(metadataAccountId, BitcoinTransactionId("remote-tx"))
      .shouldBeOk()?.note.shouldBe("Old note")
    f8eClient.storedBackup.shouldNotBeNull().sealedSsek.shouldBe(sealedSsek)
    service.remediation.value.shouldBe(WalletMetadataRemediation.None)
  }

  test("sync bootstraps an empty fresh install from remote metadata") {
    databaseProvider.database().walletMetadataQueries.clear()
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("remote-tx"),
      note = "From another device",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2)
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          transactionNotes = setOf(remoteNote),
          updatedAt = remoteNote.updatedAt
        )
      )
    )

    service.syncPendingBackups().shouldBeOk()

    walletMetadataDao.transactionNote(metadataAccountId, remoteNote.transactionId)
      .shouldBeOk() shouldBe remoteNote
    f8eClient.putCalls shouldBe emptyList()
  }

  test("sync uploads the union when local and remote contain different records") {
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("remote-tx"),
      note = "Remote",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2)
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          transactionNotes = setOf(remoteNote),
          updatedAt = remoteNote.updatedAt
        )
      )
    )

    service.syncPendingBackups().shouldBeOk()

    val uploaded = f8eClient.putCalls.single().second
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, uploaded).shouldBeOk()
    walletMetadataDao.snapshot(metadataAccountId).shouldBeOk()
      ?.transactionNotes?.map { it.transactionId }?.toSet() shouldBe
      setOf(transactionId, remoteNote.transactionId)
  }

  test("a remote deletion removes the local note and is not re-uploaded") {
    val deletion = WalletMetadataTombstone.DeletedTransactionNote(
      transactionId = transactionId,
      deletedAt = Instant.fromEpochMilliseconds(1)
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          tombstones = setOf(deletion),
          updatedAt = deletion.deletedAt
        )
      )
    )

    service.syncPendingBackups().shouldBeOk()

    walletMetadataDao.transactionNote(metadataAccountId, transactionId).shouldBeOk() shouldBe null
    walletMetadataDao.tombstones(metadataAccountId).shouldBeOk() shouldBe setOf(deletion)
    f8eClient.putCalls shouldBe emptyList()
  }

  test("feature flag off skips pull and push") {
    transactionNotesFeatureFlag.setFlagValue(false)
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()

    f8eClient.getCalls shouldBe emptyList()
    f8eClient.putCalls shouldBe emptyList()
  }

  test("no active full account skips pull and push") {
    accountService.clear()
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()

    f8eClient.getCalls shouldBe emptyList()
    f8eClient.putCalls shouldBe emptyList()
  }

  test("unsupported remote snapshot version is skipped and never downgraded") {
    f8eClient.getResult = Ok(
      sealedJson(
        """{"version":2,"accountId":"${metadataAccountId.value}","transactionNotes":[],"tombstones":[],"updatedAt":"1970-01-01T00:00:00Z","futureField":"keep-me"}"""
      )
    )
    service.requestBackup(metadataAccountId).shouldBeOk()

    service.syncPendingBackups().shouldBeOk()

    f8eClient.getCalls shouldBe listOf(FullAccountMock.accountId)
    f8eClient.putCalls shouldBe emptyList()
  }

  test("invalid remote records are skipped while valid records merge") {
    f8eClient.getResult = Ok(
      sealedJson(
        """{"version":1,"accountId":"${metadataAccountId.value}","transactionNotes":[{"transactionId":"valid","note":"Good","createdAt":"1970-01-01T00:00:00Z","updatedAt":"1970-01-01T00:00:01Z"},{"transactionId":"","note":"secret invalid content","createdAt":"1970-01-01T00:00:00Z","updatedAt":"1970-01-01T00:00:01Z"}],"tombstones":[],"updatedAt":"1970-01-01T00:00:01Z"}"""
      )
    )

    service.syncPendingBackups().shouldBeOk()

    walletMetadataDao.transactionNote(metadataAccountId, BitcoinTransactionId("valid"))
      .shouldBeOk()?.note shouldBe "Good"
    // Preserve the malformed server object rather than sanitizing it destructively.
    f8eClient.putCalls.shouldBe(emptyList())
  }

  test("an edit landing during pull and merge remains pending and is uploaded") {
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("remote-tx"),
      note = "Remote",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2)
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          transactionNotes = setOf(remoteNote),
          updatedAt = remoteNote.updatedAt
        )
      )
    )
    val concurrentNote = TransactionNote(
      transactionId = BitcoinTransactionId("concurrent-tx"),
      note = "Concurrent",
      createdAt = Instant.fromEpochMilliseconds(3),
      updatedAt = Instant.fromEpochMilliseconds(3)
    )
    f8eClient.onGet = {
      walletMetadataDao.upsertTransactionNote(metadataAccountId, concurrentNote).shouldBeOk()
      service.requestBackup(metadataAccountId).shouldBeOk()
    }

    service.syncPendingBackups().shouldBeOk()

    walletMetadataDao.transactionNote(metadataAccountId, concurrentNote.transactionId)
      .shouldBeOk() shouldBe concurrentNote
    f8eClient.putCalls.size shouldBe 1
  }

  test("restoreFromPrefetchedBackup applies backup locally without a network GET") {
    // Upload first so we have a validly-sealed WalletMetadataBackup to restore from.
    service.requestBackup(metadataAccountId).shouldBeOk()
    service.syncPendingBackups().shouldBeOk()
    val backup = f8eClient.putCalls.last().second

    val getCallsBefore = f8eClient.getCalls.size

    // restoreFromPrefetchedBackup must not issue a GET
    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, backup).shouldBeOk()
    f8eClient.getCalls.size.shouldBe(getCallsBefore) // no additional GET
  }

  test("lost-app restore merges instead of clobbering local metadata") {
    service.requestBackup(metadataAccountId).shouldBeOk()
    service.syncPendingBackups().shouldBeOk()
    val backup = f8eClient.putCalls.last().second
    val laterLocalNote = TransactionNote(
      transactionId = BitcoinTransactionId("later-local"),
      note = "Keep me",
      createdAt = Instant.fromEpochMilliseconds(5),
      updatedAt = Instant.fromEpochMilliseconds(5)
    )
    walletMetadataDao.upsertTransactionNote(metadataAccountId, laterLocalNote).shouldBeOk()

    service.restoreFromPrefetchedBackup(FullAccountMock.accountId, backup).shouldBeOk()

    walletMetadataDao.snapshot(metadataAccountId).shouldBeOk()
      ?.transactionNotes?.map { it.transactionId }?.toSet() shouldBe
      setOf(transactionId, laterLocalNote.transactionId)
  }

  test("restoreFromPrefetchedBackup succeeds and stores the snapshot locally") {
    service.requestBackup(metadataAccountId).shouldBeOk()
    service.syncPendingBackups().shouldBeOk()
    val backup = f8eClient.putCalls.last().second

    val result = service.restoreFromPrefetchedBackup(FullAccountMock.accountId, backup)
    result.shouldBeOk()
    result.value.shouldNotBeNull()
  }

  test("second installation converges after first post-recovery note") {
    databaseProvider.database().walletMetadataQueries.clear()
    val firstInstallationDao = WalletMetadataDaoImpl(databaseProvider)
    val firstInstallationService = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = firstInstallationDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake(),
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )
    firstInstallationService.provision(FullAccountMock.accountId, sealedSsek, FullAccountMock.config.f8eEnvironment).shouldBeOk()
    val postRecoveryNote = TransactionNote(
      transactionId = BitcoinTransactionId("post-recovery-note"),
      note = "Made after recovery",
      createdAt = Instant.fromEpochMilliseconds(10),
      updatedAt = Instant.fromEpochMilliseconds(10)
    )
    firstInstallationDao.upsertTransactionNote(metadataAccountId, postRecoveryNote).shouldBeOk()
    firstInstallationService.requestBackup(metadataAccountId).shouldBeOk()
    firstInstallationService.syncPendingBackups().shouldBeOk()

    // Clear only local metadata, preserving the fake server object and local SSEK as a second
    // recovered installation would have after its one required hardware unseal.
    databaseProvider.database().walletMetadataQueries.clear()
    val secondInstallationDao = WalletMetadataDaoImpl(databaseProvider)
    val secondInstallationService = WalletMetadataServerBackupServiceImpl(
      accountService = accountService,
      accountConfigService = accountConfigService,
      walletMetadataBackupF8eClient = f8eClient,
      listKeysetsF8eClient = listKeysetsF8eClient,
      walletMetadataDao = secondInstallationDao,
      ssekDao = ssekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake(),
      transactionNotesFeatureFlag = transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag =
        transactionNotesSyncFrequencySecondsFeatureFlag
    )

    secondInstallationService.syncPendingBackups().shouldBeOk()

    secondInstallationDao.transactionNote(metadataAccountId, postRecoveryNote.transactionId)
      .shouldBeOk(postRecoveryNote)
    secondInstallationService.remediation.value.shouldBe(WalletMetadataRemediation.None)
  }

  test("an edit landing during an upload is not lost when the upload completes") {
    // Regression test for the stale-upload race: syncPendingBackups reads the snapshot,
    // uploads it, then clears the pending marker. If an edit (requestBackup) lands between
    // the snapshot read and the marker clear, the uploaded blob is stale and the pending
    // marker must NOT be cleared, or the newer edit would never be retried.
    service.requestBackup(metadataAccountId).shouldBeOk()

    // Simulate a concurrent edit arriving while the PUT is in flight.
    f8eClient.onPut = {
      walletMetadataDao.upsertTransactionNote(
        accountId = metadataAccountId,
        note = TransactionNote(
          transactionId = transactionId,
          note = "Coffee with Sam",
          createdAt = Instant.fromEpochMilliseconds(0),
          updatedAt = Instant.fromEpochMilliseconds(1)
        )
      )
      service.requestBackup(metadataAccountId).shouldBeOk()
    }

    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(1)

    // The pending marker survived the stale upload: the next run re-uploads with the
    // newer note content.
    f8eClient.onPut = null
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(2)

    // And after the fresh upload, the marker is cleared.
    service.syncPendingBackups().shouldBeOk()
    f8eClient.putCalls.size.shouldBe(2)
  }

  test("restoreFromServerBackup returns null when the server has no backup") {
    f8eClient.getResult = Ok(null)

    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeOk(null)

    f8eClient.getCalls shouldBe listOf(FullAccountMock.accountId)
  }

  test("restoreFromServerBackup fetches, unseals, and persists the restored notes") {
    val remoteNote = TransactionNote(
      transactionId = BitcoinTransactionId("restored-tx"),
      note = "Restored",
      createdAt = Instant.fromEpochMilliseconds(1),
      updatedAt = Instant.fromEpochMilliseconds(2)
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          transactionNotes = setOf(remoteNote),
          updatedAt = remoteNote.updatedAt
        )
      )
    )

    val result = service.restoreFromServerBackup(FullAccountMock.accountId)
    result.shouldBeOk()
    result.value.shouldNotBeNull()

    walletMetadataDao.transactionNote(metadataAccountId, remoteNote.transactionId)
      .shouldBeOk() shouldBe remoteNote
  }

  test("restoreFromServerBackup uses the active full account environment") {
    accountConfigService.setF8eEnvironment(Production).shouldBeOk()
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = KeyboxMock.copy(
          config = KeyboxMock.config.copy(f8eEnvironment = Staging)
        )
      )
    )
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = metadataAccountId,
          transactionNotes = setOf(
            TransactionNote(
              transactionId = BitcoinTransactionId("restored-tx"),
              note = "Restored",
              createdAt = Instant.fromEpochMilliseconds(1),
              updatedAt = Instant.fromEpochMilliseconds(1)
            )
          ),
          updatedAt = Instant.fromEpochMilliseconds(1)
        )
      )
    )

    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeOk()

    f8eClient.getEnvironments.shouldBe(listOf(Staging))
  }

  test("restoreFromServerBackup fails when the snapshot cannot be unsealed") {
    f8eClient.getResult = Ok(
      WalletMetadataBackup(
        sealedSsek = "unknown-sealed-ssek".encodeUtf8(),
        sealedWalletMetadataSnapshot = symmetricKeyEncryptor.seal(
          unsealedData = "garbage".encodeUtf8(),
          key = SymmetricKeyImpl(raw = "some-other-key".encodeUtf8()),
          aad = "Bitkey Wallet Metadata Server Backup Encryption Version 1.0".encodeUtf8()
        )
      )
    )

    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeErrOfType<Error>()
  }

  test("restore rejects a snapshot that belongs to a different account") {
    f8eClient.getResult = Ok(
      sealedBackup(
        WalletMetadataSnapshot(
          accountId = WalletMetadataAccountId("someone-elses-account"),
          transactionNotes = setOf(
            TransactionNote(
              transactionId = BitcoinTransactionId("foreign-tx"),
              note = "Not yours",
              createdAt = Instant.fromEpochMilliseconds(1),
              updatedAt = Instant.fromEpochMilliseconds(1)
            )
          ),
          updatedAt = Instant.fromEpochMilliseconds(1)
        )
      )
    )

    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeErrOfType<Error>()

    // The foreign note must not have been persisted locally.
    walletMetadataDao.transactionNote(metadataAccountId, BitcoinTransactionId("foreign-tx"))
      .shouldBeOk() shouldBe null
  }

  test("isRestoring is true during restore and cleared afterwards") {
    var restoringDuringFetch: Boolean? = null
    f8eClient.onGet = { restoringDuringFetch = service.isRestoring.value }

    service.isRestoring.value shouldBe false
    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeOk()

    restoringDuringFetch shouldBe true
    service.isRestoring.value shouldBe false
  }

  test("isRestoring is cleared even when restore fails") {
    f8eClient.getResult = Err(HttpError.NetworkError(Throwable("offline")))

    service.restoreFromServerBackup(FullAccountMock.accountId).shouldBeErrOfType<Error>()

    service.isRestoring.value shouldBe false
  }
})


private fun WalletMetadataSnapshot.toRaw(): RawWalletMetadataSnapshot =
  RawWalletMetadataSnapshot(
    version = version,
    accountId = accountId.value,
    transactionNotes = transactionNotes.map { note ->
      RawTransactionNote(
        transactionId = note.transactionId.value,
        note = note.note,
        createdAt = note.createdAt.toString(),
        updatedAt = note.updatedAt.toString()
      )
    },
    tombstones = tombstones
      .filterIsInstance<WalletMetadataTombstone.DeletedTransactionNote>()
      .map { tombstone ->
        RawDeletedTransactionNote(
          transactionId = tombstone.transactionId.value,
          deletedAt = tombstone.deletedAt.toString()
        )
      },
    updatedAt = updatedAt.toString()
  )

@Serializable
private data class RawWalletMetadataSnapshot(
  val version: UInt = WalletMetadataSnapshot.CURRENT_VERSION,
  val accountId: String,
  val transactionNotes: List<RawTransactionNote> = emptyList(),
  val tombstones: List<RawDeletedTransactionNote> = emptyList(),
  val updatedAt: String? = null,
)

@Serializable
private data class RawTransactionNote(
  val transactionId: String,
  val note: String,
  val createdAt: String,
  val updatedAt: String,
)

@Serializable
private data class RawDeletedTransactionNote(
  val transactionId: String,
  val deletedAt: String,
)

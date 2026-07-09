package build.wallet.recovery

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.recovery.LocalRecoveryAttemptProgress.*
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.CreatedSpendingKeys
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.HwDescriptorValidated
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class RecoveryDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  val keyset = SpendingKeysetMock
  val serverRecovery = LostHardwareServerRecoveryMock
  val sealedCsek = "sealedCsek".encodeUtf8()
  val sealedSsek = "sealedSsek".encodeUtf8()
  val customerAccount = serverRecovery.fullAccountId
  val appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey
  val appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey
  val hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey
  val serverSpendingKeyset = F8eSpendingKeysetMock
  val originalAppGlobalAuthKey = PublicKey<AppGlobalAuthKey>("original-app-global-auth-key")
  val spendingKeyset1 = SpendingKeyset(
    localId = "keyset-app-1",
    f8eSpendingKeyset =
      F8eSpendingKeyset(
        keysetId = "keyset-server-1",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1")),
        privateWalletRootXpub = "tpub-private-root-xpub-1"
      ),
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-1")),
    hardwareKey =
      HwSpendingPublicKey(
        DescriptorPublicKeyMock("hw-dpub-1", fingerprint = "deadbeef")
      )
  )
  val privateWalletRootXpubValues = listOf("tpub-private-root-xpub-2", null)

  lateinit var databaseProvider: BitkeyDatabaseProviderImpl
  lateinit var dao: RecoveryDaoImpl

  beforeTest {
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao =
      RecoveryDaoImpl(
        databaseProvider
      )
  }

  privateWalletRootXpubValues.forEach { privateWalletRootXpub ->
    val contextDescription = privateWalletRootXpub ?: "null"

    context("privateWalletRootXpub = $contextDescription") {
      val spendingKeyset2 =
        SpendingKeyset(
          localId = "keyset-app-2",
          f8eSpendingKeyset =
            F8eSpendingKeyset(
              keysetId = "keyset-server-2",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-2")),
              privateWalletRootXpub = privateWalletRootXpub
            ),
          networkType = SIGNET,
          appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-2")),
          hardwareKey =
            HwSpendingPublicKey(
              DescriptorPublicKeyMock("hw-dpub-2", fingerprint = "deadbeef")
            )
        )
      val keysets = listOf(spendingKeyset1, spendingKeyset2)

      test("setLocalRecoveryProgress: Initiated") {
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          setProgressInitiated(
            dao,
            customerAccount,
            keyset,
            appGlobalAuthKey,
            appRecoveryAuthKey,
            hardwareAuthKey,
            originalAppGlobalAuthKey
          )

          dao.setActiveServerRecovery(serverRecovery)

          awaitItem().shouldBe(
            Ok(
              Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery(
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                serverRecovery = serverRecovery,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)

          setProgressRotatedAuth(dao)

          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.RotatedAuthKeys(
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey,
                hardwareSpendingKeyProof = null
              )
            )
          )

          setProgressCreatedSpending(dao, serverSpendingKeyset)

          val localRecoveryAttempt =
            databaseProvider.database().recoveryQueries.getLocalRecovery().executeAsOne()
          localRecoveryAttempt.serverSpendingKey
            ?.privateWalletRootXpub
            .shouldBe(serverSpendingKeyset.privateWalletRootXpub)

          awaitItem().shouldBe(
            Ok(
              CreatedSpendingKeys(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          setProgressUploadedDescriptorBackups(dao, keysets)

          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.UploadedDescriptorBackups(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          setProgressActivatedSpending(dao, serverSpendingKeyset)

          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.ActivatedSpendingKeys(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          val sealedDdk: SealedData = "sealedDdk".encodeUtf8()
          setProgressHwDescriptorValidated(dao, sealedDdk)

          awaitItem().shouldBe(
            Ok(
              HwDescriptorValidated(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey,
                sealedDdkData = sealedDdk
              )
            )
          )

          setProgressDdkBackedUp(dao)

          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.DdkBackedUp(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          setProgressBackedUpToCloud(dao)

          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.BackedUpToCloud(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          // Now test the new SweptFunds -> SweepCompleted transition
          dao.setLocalRecoveryProgress(SweepingFunds)

          // This should create a SweepCompleted state
          awaitItem().shouldBe(
            Ok(
              ServerIndependentRecovery.SweepAttempted(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                keysets = listOf(spendingKeyset1, spendingKeyset2),
                originalAppGlobalAuthKey = originalAppGlobalAuthKey
              )
            )
          )

          setProgressCompletedRecovery(dao)

          awaitItem().shouldBe(
            Ok(
              NoActiveRecovery
            )
          )
        }
      }

      test("MaybeNoLongerRecovering") {
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          setProgressInitiated(
            dao,
            customerAccount,
            keyset,
            appGlobalAuthKey,
            appRecoveryAuthKey,
            hardwareAuthKey,
            originalAppGlobalAuthKey = null
          )

          dao.setActiveServerRecovery(serverRecovery)

          awaitItem().shouldBeOkOfType<Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery>()

          setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)

          dao.setActiveServerRecovery(null)

          awaitItem().shouldBeOkOfType<ServerIndependentRecovery.MaybeNoLongerRecovering>()
        }
      }

      test("CompletedRecovery clears W3 upgrade migration state") {
        setProgressInitiated(
          dao,
          customerAccount,
          keyset,
          appGlobalAuthKey,
          appRecoveryAuthKey,
          hardwareAuthKey,
          originalAppGlobalAuthKey
        )

        val database = databaseProvider.database()
        val w3UpgradeMigrationQueries = database.w3UpgradeMigrationQueries
        w3UpgradeMigrationQueries.saveHardwareKey(keyset.hardwareKey, newHardwareKeyProof = null)
        w3UpgradeMigrationQueries.insertSweepTransaction(
          txid = "w3-sweep-txid-1",
          broadcastTime = "2026-05-21T00:00:00Z"
        )
        w3UpgradeMigrationQueries.insertSweepTransaction(
          txid = "w3-sweep-txid-2",
          broadcastTime = null
        )
        w3UpgradeMigrationQueries.getState().executeAsOneOrNull()
          .shouldNotBeNull()
        w3UpgradeMigrationQueries.getSweepTransactions().executeAsList()
          .shouldHaveSize(2)

        setProgressCompletedRecovery(dao)

        database.recoveryQueries.getLocalRecovery().executeAsOneOrNull()
          .shouldBe(null)
        w3UpgradeMigrationQueries.getState().executeAsOneOrNull()
          .shouldBe(null)
        w3UpgradeMigrationQueries.getSweepTransactions().executeAsList()
          .shouldBeEmpty()
        database.fullAccountQueries.getActiveFullAccount().executeAsOneOrNull()
          ?.accountId.shouldBe(KeyboxMock.fullAccountId)
      }

      test("NoLongerRecovering") {
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          setProgressInitiated(
            dao,
            customerAccount,
            keyset,
            appGlobalAuthKey,
            appRecoveryAuthKey,
            hardwareAuthKey,
            originalAppGlobalAuthKey = null
          )

          dao.setActiveServerRecovery(serverRecovery)

          awaitItem().shouldBeOkOfType<Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery>()

          dao.setActiveServerRecovery(null)

          awaitItem().shouldBeOkOfType<Recovery.NoLongerRecovering>()
        }
      }

      test("HwDescriptorValidated persists and restores sealedDdkData") {
        // Verifies the bug fix: sealedDdk is round-tripped through the DB so that
        // on app restart (resume from HwDescriptorValidated checkpoint), the sealed
        // DDK is available without requiring another NFC tap.
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          setProgressInitiated(dao, customerAccount, keyset, appGlobalAuthKey, appRecoveryAuthKey, hardwareAuthKey, originalAppGlobalAuthKey)
          dao.setActiveServerRecovery(serverRecovery)
          awaitItem() // InitiatedRecovery

          setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)
          setProgressRotatedAuth(dao)
          awaitItem() // RotatedAuthKeys

          setProgressCreatedSpending(dao, serverSpendingKeyset)
          awaitItem() // CreatedSpendingKeys

          setProgressUploadedDescriptorBackups(dao, keysets)
          awaitItem() // UploadedDescriptorBackups

          setProgressActivatedSpending(dao, serverSpendingKeyset)
          awaitItem() // ActivatedSpendingKeys

          val sealedDdk: SealedData = "sealedDdkBytes".encodeUtf8()
          setProgressHwDescriptorValidated(dao, sealedDdk)

          awaitItem().shouldBe(
            Ok(
              HwDescriptorValidated(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey,
                sealedDdkData = sealedDdk
              )
            )
          )
        }
      }

      test("HwDescriptorValidated with null sealedDdk is preserved") {
        // For W1 hardware (old firmware path), the sealed DDK is obtained via a
        // separate NFC tap rather than bundled. Null sealedDdkData must survive the round-trip.
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          setProgressInitiated(dao, customerAccount, keyset, appGlobalAuthKey, appRecoveryAuthKey, hardwareAuthKey, originalAppGlobalAuthKey)
          dao.setActiveServerRecovery(serverRecovery)
          awaitItem() // InitiatedRecovery

          setProgressGeneratedCsek(dao, sealedCsek, sealedSsek)
          setProgressRotatedAuth(dao)
          awaitItem() // RotatedAuthKeys

          setProgressCreatedSpending(dao, serverSpendingKeyset)
          awaitItem() // CreatedSpendingKeys

          setProgressUploadedDescriptorBackups(dao, keysets)
          awaitItem() // UploadedDescriptorBackups

          setProgressActivatedSpending(dao, serverSpendingKeyset)
          awaitItem() // ActivatedSpendingKeys

          setProgressHwDescriptorValidated(dao, sealedDdkData = null)

          awaitItem().shouldBe(
            Ok(
              HwDescriptorValidated(
                f8eSpendingKeyset = serverSpendingKeyset,
                fullAccountId = serverRecovery.fullAccountId,
                appSpendingKey = keyset.appKey,
                appGlobalAuthKey = serverRecovery.destinationAppGlobalAuthPubKey,
                appRecoveryAuthKey = serverRecovery.destinationAppRecoveryAuthPubKey,
                hardwareSpendingKey = keyset.hardwareKey,
                hardwareAuthKey = serverRecovery.destinationHardwareAuthPubKey,
                appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
                factorToRecover = Hardware,
                sealedCsek = sealedCsek,
                sealedSsek = sealedSsek,
                keysets = keysets,
                originalAppGlobalAuthKey = originalAppGlobalAuthKey,
                sealedDdkData = null
              )
            )
          )
        }
      }

      test("SomeoneElseIsRecovering") {
        // This test demonstrates the DAO behavior that causes the race condition:
        // When server recovery is set WITHOUT local recovery being present,
        // the DAO returns SomeoneElseIsRecovering.
        //
        // The race condition occurs when:
        // 1. User initiates recovery
        // 2. Server is notified and returns recovery status
        // 3. Sync worker saves server recovery BEFORE local recovery is saved
        // 4. User incorrectly sees "SomeoneElseIsRecovering"
        //
        // The fix in RecoveryStatusServiceImpl prevents this by checking if
        // local recovery is present before setting server recovery. See the
        // tests in RecoveryStatusServiceImplTests for the service-level fix.
        dao.activeRecovery().test {
          awaitItem().shouldBe(Ok(NoActiveRecovery))

          dao.setActiveServerRecovery(LostHardwareServerRecoveryMock)

          awaitItem()
            .shouldBeOkOfType<Recovery.SomeoneElseIsRecovering>()
        }
      }
    }
  }
})

private suspend fun setProgressInitiated(
  dao: RecoveryDaoImpl,
  fullAccountId: FullAccountId,
  keyset: SpendingKeyset,
  appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
  appRecoveryAuthPublicKey: PublicKey<AppRecoveryAuthKey>,
  hwAuthPublicKey: HwAuthPublicKey,
  originalAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
) {
  dao.setLocalRecoveryProgress(
    CreatedPendingKeybundles(
      fullAccountId,
      appKeyBundle =
        AppKeyBundle(
          localId = "app-key-bundle",
          spendingKey = keyset.appKey,
          authKey = appGlobalAuthPublicKey,
          networkType = BITCOIN,
          recoveryAuthKey = appRecoveryAuthPublicKey
        ),
      hwKeyBundle =
        HwKeyBundle(
          localId = "hw-key-bundle",
          spendingKey = keyset.hardwareKey,
          authKey = hwAuthPublicKey,
          networkType = BITCOIN
        ),
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      lostFactor = Hardware,
      originalAppGlobalAuthKey = originalAppGlobalAuthKey
    )
  )
}

private suspend fun setProgressGeneratedCsek(
  dao: RecoveryDaoImpl,
  sealedCsek: SealedCsek,
  sealedSsek: SealedSsek,
) {
  dao.setLocalRecoveryProgress(AttemptingCompletion(sealedCsek, sealedSsek))
}

private suspend fun setProgressRotatedAuth(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    RotatedAuthKeys
  )
}

private suspend fun setProgressCreatedSpending(
  dao: RecoveryDaoImpl,
  serverSpendingKeyset: F8eSpendingKeyset,
) {
  dao.setLocalRecoveryProgress(
    CreatedSpendingKeys(serverSpendingKeyset)
  )
}

private suspend fun setProgressActivatedSpending(
  dao: RecoveryDaoImpl,
  serverSpendingKeyset: F8eSpendingKeyset,
) {
  dao.setLocalRecoveryProgress(
    ActivatedSpendingKeys(serverSpendingKeyset)
  )
}

private suspend fun setProgressBackedUpToCloud(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    BackedUpToCloud
  )
}

private suspend fun setProgressCompletedRecovery(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    CompletedRecovery(
      keyboxToActivate = KeyboxMock
    )
  )
}

private suspend fun setProgressUploadedDescriptorBackups(
  dao: RecoveryDaoImpl,
  keysets: List<SpendingKeyset>,
) {
  dao.setLocalRecoveryProgress(
    UploadedDescriptorBackups(keysets)
  )
}

private suspend fun setProgressHwDescriptorValidated(
  dao: RecoveryDaoImpl,
  sealedDdkData: SealedData?,
) {
  dao.setLocalRecoveryProgress(
    HwDescriptorValidated(
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      sealedDdkData = sealedDdkData
    )
  )
}

private suspend fun setProgressDdkBackedUp(dao: RecoveryDaoImpl) {
  dao.setLocalRecoveryProgress(
    DdkBackedUp
  )
}

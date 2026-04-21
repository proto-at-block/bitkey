package build.wallet.wallet.migration

import bitkey.account.HardwareType
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.database.sqldelight.saveKeyboxAsActive
import build.wallet.db.DbError
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class W3UpgradeCheckpointWriterImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val uuidGenerator = UuidGeneratorFake()
  val clock = ClockFake()

  lateinit var databaseProvider: BitkeyDatabaseProviderImpl
  lateinit var writer: W3UpgradeCheckpointWriterImpl
  lateinit var w3UpgradeDao: W3UpgradeDaoImpl

  fun newKeyset(
    localId: String = "w3-keyset-local-id",
  ): SpendingKeyset =
    SpendingKeysetMock.copy(
      localId = localId,
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = HwKeyBundleMock.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

  fun updatedKeybox(newKeyset: SpendingKeyset) =
    FullAccountMock.keybox
      .withNewSpendingKeyset(newKeyset)
      .copy(
        localId = "w3-keybox-local-id",
        config = FullAccountMock.keybox.config.copy(hardwareType = HardwareType.W3)
      )

  suspend fun seedExistingLocalState() {
    val database = databaseProvider.database()
    database.awaitTransaction {
      saveKeyboxAsActive(FullAccountMock.keybox)
      appInstallationQueries.initializeAppInstallationIfAbsent("existing-installation-id")
      appInstallationQueries.updateHardwareSerialNumber("old-device-serial")
      hardwareUnlockMethodsQueries.insertHardwareUnlockMethod(
        unlockMethod = UnlockMethod.BIOMETRICS,
        unlockMethodIdx = 0,
        createdAt = clock.now()
      )
    }
  }

  suspend fun appInstallationHardwareSerial() =
    databaseProvider.database()
      .appInstallationQueries
      .getAppInstallation()
      .executeAsOneOrNull()
      ?.hardwareSerialNumber

  suspend fun allUnlockInfo() =
    databaseProvider.database()
      .hardwareUnlockMethodsQueries
      .selectAll()
      .executeAsList()
      .map {
        UnlockInfo(
          unlockMethod = it.unlockMethod,
          fingerprintIdx = it.unlockMethodIdx?.toInt()
        )
      }

  beforeTest {
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    writer = W3UpgradeCheckpointWriterImpl(
      databaseProvider = databaseProvider,
      uuidGenerator = uuidGenerator,
      clock = clock
    )
    w3UpgradeDao = W3UpgradeDaoImpl(databaseProvider)
    uuidGenerator.reset()
  }

  test("persistCreateNewKeysetCheckpoint writes W3 checkpoint state across all local stores") {
    val newKeyset = newKeyset()
    val updatedKeybox = updatedKeybox(newKeyset)

    writer.persistCreateNewKeysetCheckpoint(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hardware-fingerprint",
      newDeviceSerial = "new-device-serial",
      newKeyset = newKeyset,
      updatedKeybox = updatedKeybox,
      sealedSsekForDecryption = null
    ).shouldBeOk()

    val migrationState = w3UpgradeDao.currentState().first().get().shouldNotBeNull()
    migrationState.oldDeviceSerial.shouldBe("old-device-serial")
    migrationState.oldHardwareFingerprint.shouldBe("old-hardware-fingerprint")
    migrationState.newHardwareKey.shouldBe(newKeyset.hardwareKey)
    migrationState.newAppKey.shouldBe(newKeyset.appKey)
    migrationState.newServerKey.shouldBe(newKeyset.f8eSpendingKeyset)
    migrationState.keysetLocalId.shouldBe(newKeyset.localId)

    appInstallationHardwareSerial().shouldBe("new-device-serial")
    allUnlockInfo().shouldBe(UnlockInfo.ONBOARDING_DEFAULT)

    val activeFullAccount = databaseProvider.database()
      .fullAccountQueries
      .getActiveFullAccount()
      .executeAsOne()
    activeFullAccount.accountId.shouldBe(updatedKeybox.fullAccountId)
    activeFullAccount.keyboxId.shouldBe(updatedKeybox.localId)
    activeFullAccount.hardwareType.shouldBe(HardwareType.W3)
    activeFullAccount.spendingPublicKeysetId.shouldBe(newKeyset.localId)
  }

  test("persistCreateNewKeysetCheckpoint rolls back all local writes when saving the new keybox fails") {
    seedExistingLocalState()

    val conflictingKeyset = newKeyset(localId = FullAccountMock.keybox.activeSpendingKeyset.localId)
    val conflictingKeybox = updatedKeybox(conflictingKeyset)

    writer.persistCreateNewKeysetCheckpoint(
      oldDeviceSerial = "new-old-device-serial",
      oldHardwareFingerprint = "new-old-hardware-fingerprint",
      newDeviceSerial = "new-device-serial",
      newKeyset = conflictingKeyset,
      updatedKeybox = conflictingKeybox,
      sealedSsekForDecryption = null
    ).shouldBeErrOfType<DbError>()

    w3UpgradeDao.currentState().first().get().shouldBe(null)
    appInstallationHardwareSerial().shouldBe("old-device-serial")
    allUnlockInfo().shouldBe(listOf(UnlockInfo(UnlockMethod.BIOMETRICS, 0)))

    val activeFullAccount = databaseProvider.database()
      .fullAccountQueries
      .getActiveFullAccount()
      .executeAsOne()
    activeFullAccount.keyboxId.shouldBe(FullAccountMock.keybox.localId)
    activeFullAccount.spendingPublicKeysetId.shouldBe(FullAccountMock.keybox.activeSpendingKeyset.localId)
  }
})

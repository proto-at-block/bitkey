package build.wallet.wallet.migration

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.saveKeyboxAsActive
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockInfo
import build.wallet.platform.random.UuidGenerator
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock

/**
 * Batches all W3 upgrade keyset-creation side effects into a single atomic database transaction.
 *
 * During a W3 upgrade the app must persist several pieces of state at once — W3 migration
 * progress (old/new device identity, keyset keys), app-installation hardware serial, hardware
 * unlock methods, and the updated keybox. Writing these through their individual DAOs would
 * create separate transactions, so a crash between any two writes would leave the database in
 * an inconsistent state. This writer bypasses the DAOs intentionally and issues all queries
 * inside one [awaitTransaction] for all-or-nothing semantics.
 */
interface W3UpgradeCheckpointWriter {
  /**
   * Atomically persists everything produced by the CreateNewKeyset step of a W3 upgrade.
   *
   * If any single write fails the entire transaction is rolled back and the caller can
   * safely retry or show an error without partial state left behind.
   */
  suspend fun persistCreateNewKeysetCheckpoint(
    oldDeviceSerial: String,
    oldHardwareFingerprint: String,
    newDeviceSerial: String,
    newKeyset: SpendingKeyset,
    updatedKeybox: Keybox,
  ): Result<Unit, DbError>
}

@BitkeyInject(AppScope::class)
class W3UpgradeCheckpointWriterImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val uuidGenerator: UuidGenerator,
  private val clock: Clock,
) : W3UpgradeCheckpointWriter {
  override suspend fun persistCreateNewKeysetCheckpoint(
    oldDeviceSerial: String,
    oldHardwareFingerprint: String,
    newDeviceSerial: String,
    newKeyset: SpendingKeyset,
    updatedKeybox: Keybox,
  ): Result<Unit, DbError> {
    val createdAt = clock.now()
    return databaseProvider.database().awaitTransaction {
      w3UpgradeMigrationQueries.saveOldDeviceSerial(oldDeviceSerial)
      w3UpgradeMigrationQueries.saveOldHardwareFingerprint(oldHardwareFingerprint)
      w3UpgradeMigrationQueries.saveHardwareKey(newKeyset.hardwareKey)
      w3UpgradeMigrationQueries.saveAppKey(newKeyset.appKey)
      w3UpgradeMigrationQueries.saveServerKey(newKeyset.f8eSpendingKeyset)
      w3UpgradeMigrationQueries.setKeysetLocalId(newKeyset.localId)

      appInstallationQueries.initializeAppInstallationIfAbsent(uuidGenerator.random())
      appInstallationQueries.updateHardwareSerialNumber(newDeviceSerial)

      hardwareUnlockMethodsQueries.clearHardwareUnlockMethods()
      UnlockInfo.ONBOARDING_DEFAULT.forEach { unlockInfo ->
        hardwareUnlockMethodsQueries.insertHardwareUnlockMethod(
          unlockMethod = unlockInfo.unlockMethod,
          unlockMethodIdx = unlockInfo.fingerprintIdx?.toLong(),
          createdAt = createdAt
        )
      }

      saveKeyboxAsActive(updatedKeybox)
    }
  }
}

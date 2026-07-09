package build.wallet.wallet.migration

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
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
    sealedSsekForDecryption: SealedSsek?,
  ): Result<Unit, DbError> {
    val createdAt = clock.now()
    return databaseProvider.database().awaitTransaction {
      w3UpgradeMigrationQueries.saveOldDeviceSerial(oldDeviceSerial)
      w3UpgradeMigrationQueries.saveOldHardwareFingerprint(oldHardwareFingerprint)
      // Hardware key + attestation proof are persisted incrementally in
      // `MigrationServiceImpl.createNewKeyset` before `createKeyset` runs, so we do NOT
      // re-save them here — re-running `saveHardwareKey` with `newHardwareKeyProof = null`
      // would clobber the real proof saved earlier.
      w3UpgradeMigrationQueries.saveAppKey(newKeyset.appKey)
      w3UpgradeMigrationQueries.saveServerKey(newKeyset.f8eSpendingKeyset)
      w3UpgradeMigrationQueries.setKeysetLocalId(newKeyset.localId)
      w3UpgradeMigrationQueries.setSealedSsekForDecryption(sealedSsekForDecryption)

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

  override suspend fun persistCloudRestoreCheckpoint(keybox: Keybox): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      saveKeyboxAsActive(keybox)
      w3UpgradeMigrationQueries.markResumedFromCloudBackup()
    }
  }
}

package build.wallet.wallet.migration

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.db.DbError
import build.wallet.db.DbTransactionError
import build.wallet.firmware.UnlockInfo
import build.wallet.keybox.KeyboxDaoMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class W3UpgradeCheckpointWriterIntegrationFake(
  private val w3UpgradeDao: W3UpgradeDaoFake,
  private val keyboxDao: KeyboxDaoMock,
) : W3UpgradeCheckpointWriter {
  var shouldFailPersist = false
  var persistedNewDeviceSerial: String? = null
  var persistedUnlockInfo: List<UnlockInfo>? = null

  override suspend fun persistCreateNewKeysetCheckpoint(
    oldDeviceSerial: String,
    oldHardwareFingerprint: String,
    newDeviceSerial: String,
    newKeyset: SpendingKeyset,
    updatedKeybox: Keybox,
    sealedSsekForDecryption: SealedSsek?,
  ): Result<Unit, DbError> {
    if (shouldFailPersist) {
      return Err(DbTransactionError(Exception("Failed to persist W3 checkpoint")))
    }

    w3UpgradeDao.saveOldDeviceSerial(oldDeviceSerial)
    w3UpgradeDao.saveOldHardwareFingerprint(oldHardwareFingerprint)
    // Hardware key + proof are persisted incrementally by MigrationServiceImpl; mirror prod.
    w3UpgradeDao.saveAppKey(newKeyset.appKey)
    w3UpgradeDao.saveServerKey(newKeyset.f8eSpendingKeyset)
    w3UpgradeDao.saveKeysetLocalId(newKeyset.localId)
    w3UpgradeDao.setSealedSsekForDecryption(sealedSsekForDecryption)
    keyboxDao.saveKeyboxAsActive(updatedKeybox)

    persistedNewDeviceSerial = newDeviceSerial
    persistedUnlockInfo = UnlockInfo.ONBOARDING_DEFAULT
    return Ok(Unit)
  }

  override suspend fun persistCloudRestoreCheckpoint(keybox: Keybox): Result<Unit, DbError> {
    if (shouldFailPersist) {
      return Err(DbTransactionError(Exception("Failed to persist cloud restore checkpoint")))
    }

    keyboxDao.saveKeyboxAsActive(keybox)
    w3UpgradeDao.markResumedFromCloudBackup()
    return Ok(Unit)
  }

  fun reset() {
    shouldFailPersist = false
    persistedNewDeviceSerial = null
    persistedUnlockInfo = null
  }
}

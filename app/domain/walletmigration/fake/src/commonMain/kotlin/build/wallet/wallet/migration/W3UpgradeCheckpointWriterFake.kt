package build.wallet.wallet.migration

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.db.DbError
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class W3UpgradeCheckpointWriterFake : W3UpgradeCheckpointWriter {
  var shouldFailPersist = false
  var persistCreateNewKeysetCheckpointCalls = 0
  var persistCloudRestoreCheckpointCalls = 0
  var lastPersistedKeybox: Keybox? = null

  override suspend fun persistCreateNewKeysetCheckpoint(
    oldDeviceSerial: String,
    oldHardwareFingerprint: String,
    newDeviceSerial: String,
    newKeyset: SpendingKeyset,
    updatedKeybox: Keybox,
    sealedSsekForDecryption: SealedSsek?,
  ): Result<Unit, DbError> {
    persistCreateNewKeysetCheckpointCalls++
    if (shouldFailPersist) {
      return Err(DbTransactionError(Exception("Failed to persist W3 checkpoint")))
    }
    lastPersistedKeybox = updatedKeybox
    return Ok(Unit)
  }

  override suspend fun persistCloudRestoreCheckpoint(keybox: Keybox): Result<Unit, DbError> {
    persistCloudRestoreCheckpointCalls++
    if (shouldFailPersist) {
      return Err(DbTransactionError(Exception("Failed to persist cloud restore checkpoint")))
    }
    lastPersistedKeybox = keybox
    return Ok(Unit)
  }

  fun reset() {
    shouldFailPersist = false
    persistCreateNewKeysetCheckpointCalls = 0
    persistCloudRestoreCheckpointCalls = 0
    lastPersistedKeybox = null
  }
}

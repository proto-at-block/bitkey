package build.wallet.wallet.migration

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.db.DbError
import com.github.michaelbull.result.Result

/**
 * Batches W3 upgrade side effects into single atomic database transactions.
 *
 * During a W3 upgrade the app must persist several pieces of state at once.
 * Writing these through their individual DAOs would create separate transactions,
 * so a crash between any two writes would leave the database in an inconsistent
 * state. This writer bypasses the DAOs intentionally and issues all queries
 * inside one transaction for all-or-nothing semantics.
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
    sealedSsekForDecryption: SealedSsek?,
  ): Result<Unit, DbError>

  /**
   * Atomically saves the restored [keybox] as active and marks the W3 upgrade as
   * resumed from cloud backup. Both writes commit or neither does, preventing
   * inconsistent state if either operation fails independently.
   */
  suspend fun persistCloudRestoreCheckpoint(keybox: Keybox): Result<Unit, DbError>
}

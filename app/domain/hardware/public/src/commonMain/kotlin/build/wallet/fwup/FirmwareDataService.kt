package build.wallet.fwup

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages interactions with the firmware data domain, primarily device information and pending
 * firmware updates.
 */
interface FirmwareDataService {
  /**
   * Apply the [FwupData.version] to the persisted firmware device info and
   * clear any pending fwup updates. Should only be called after a successful fwup.
   */
  suspend fun updateFirmwareVersion(fwupData: FwupData): Result<Unit, Error>

  /**
   * Retrieve the latest firmware data.
   */
  fun firmwareData(): StateFlow<FirmwareData>

  /**
   * Check if there's a pending FWUP.
   *
   * [FirmwareDataService.firmwareData] will emit an updated [FirmwareData] if it
   * has changed.
   */
  suspend fun syncLatestFwupData(): Result<Unit?, Error>

  /**
   * Checks if the active keybox has a hardware provisioned app auth key.
   *
   * This compares the keys in the active keybox against the hardware provisioned keys
   * recorded in the database via a SQL join. If no matching record exists, it means
   * the hardware has not been provisioned with the current app auth key.
   *
   * @return true if the active keybox has a matching hardware provisioned key record, false otherwise
   */
  suspend fun hasProvisionedKey(): Result<Boolean, DbError>
}

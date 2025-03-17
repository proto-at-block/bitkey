package build.wallet.fwup

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
}

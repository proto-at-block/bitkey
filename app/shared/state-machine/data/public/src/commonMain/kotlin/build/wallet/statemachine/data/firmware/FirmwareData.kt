package build.wallet.statemachine.data.firmware

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.FwupData
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate

/**
 * Describes the state of firmware for the currently activated keybox.
 *
 * @property firmwareUpdateState: The update state of the firmware
 * @property firmwareDeviceInfo: The device info of the firmware, null when no info has been stored
 * @property checkForNewFirmware: Kicks off a request to [MemfaultF8eClient] to fetch the
 * [FwupData] for the newest firmware version. If there is a newer version available, it
 * will be returned via [FirmwareUpdateState.PendingUpdate].
 */
data class FirmwareData(
  val firmwareUpdateState: FirmwareUpdateState,
  val firmwareDeviceInfo: FirmwareDeviceInfo?,
  val checkForNewFirmware: () -> Unit,
) {
  val updateVersion: String? =
    when (firmwareUpdateState) {
      is UpToDate -> null
      is PendingUpdate -> firmwareUpdateState.fwupData.version
    }

  sealed interface FirmwareUpdateState {
    /** The firmware is up to date with the latest available version.  */
    data object UpToDate : FirmwareUpdateState

    /**
     * The firmware is out of date with the latest available version and needs an update.
     * @property fwupData: The data to update the firmware with
     * @property onUpdateComplete: Callback for when the update is successfully completed.
     */
    data class PendingUpdate(
      val fwupData: FwupData,
      val onUpdateComplete: () -> Unit,
    ) : FirmwareUpdateState
  }
}

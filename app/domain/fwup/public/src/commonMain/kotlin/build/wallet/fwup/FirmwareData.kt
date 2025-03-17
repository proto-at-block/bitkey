package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate

/**
 * Describes the state of firmware for the currently activated keybox.
 *
 * @property firmwareUpdateState: The update state of the firmware
 * @property firmwareDeviceInfo: The device info of the firmware, null when no info has been stored
 */
data class FirmwareData(
  val firmwareUpdateState: FirmwareUpdateState,
  val firmwareDeviceInfo: FirmwareDeviceInfo?,
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
     */
    data class PendingUpdate(
      val fwupData: FwupData,
    ) : FirmwareUpdateState
  }
}

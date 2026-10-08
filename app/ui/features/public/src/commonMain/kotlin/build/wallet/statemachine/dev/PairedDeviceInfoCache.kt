package build.wallet.statemachine.dev

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import com.github.michaelbull.result.get

/**
 * Persists [deviceInfo] only when it belongs to the currently-paired device.
 *
 * [FirmwareDeviceInfoDao] holds the identity of the paired device, and the NFC
 * `validateHardwareIsPaired` interceptor reads its serial as the expected value when verifying
 * taps. Debug menu flows may tap an unpaired bench device, so writing that device's info here
 * would replace the paired identity and make subsequent customer-facing taps of the real
 * hardware fail as "unpaired".
 *
 * Writes when the serials match, or when no info is stored yet (nothing to protect — e.g. before
 * onboarding, where the debug menu is the expected way to populate this).
 *
 * Note this is deliberately a skip rather than a save-then-restore: restoring later would leave
 * the foreign serial in the DAO for the duration of the flow, and would not run at all if the
 * flow were abandoned partway through.
 */
suspend fun FirmwareDeviceInfoDao.setDeviceInfoIfPaired(deviceInfo: FirmwareDeviceInfo) {
  val pairedSerial = getDeviceInfo().get()?.serial
  if (pairedSerial == null || pairedSerial == deviceInfo.serial) {
    setDeviceInfo(deviceInfo)
      .logFailure { "Failed to persist device info from debug menu" }
  } else {
    logInfo {
      "Skipping device info write: tapped device is not the paired device"
    }
  }
}

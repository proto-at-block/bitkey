package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import com.github.michaelbull.result.Result

interface FirmwareDownloader {
  /**
   * Retrieves the URL for the latest version of the firmware, downloads the bundle
   * at the URL, writes the contents to the file system and unzips them.
   *
   * If there is not a newer version of firmware available, returns
   * [FirmwareDownloadError.NoUpdateNeeded]
   */
  suspend fun download(deviceInfo: FirmwareDeviceInfo): Result<Unit, FirmwareDownloadError>
}

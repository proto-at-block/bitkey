package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import com.github.michaelbull.result.Result

interface FwupDataFetcher {
  /**
   * Attempts to fetch and process the latest firmware data based on
   * the given metadata. If no update is needed, will return
   * [FwupDataFetcherError.DownloadError] with [FirmwareDownloadError.NoUpdateNeeded].
   *
   * Returns a list of [McuFwupData] for each MCU that needs updating.
   * For single-MCU devices (W1), returns a single-element list with CORE MCU data.
   * For multi-MCU devices (W3), returns data for each MCU.
   */
  suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<List<McuFwupData>, FwupDataFetcherError>

  sealed class FwupDataFetcherError : Error() {
    data class DownloadError(val error: FirmwareDownloadError) : FwupDataFetcherError()

    data class FileError(val error: Error) : FwupDataFetcherError()

    data class ParseError(val error: ParseFwupManifestError) : FwupDataFetcherError()
  }
}

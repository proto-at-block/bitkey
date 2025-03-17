package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import com.github.michaelbull.result.Result

interface FwupDataFetcher {
  /**
   * Attempts to fetch and process the latest firmware data based on
   * the given metadata. If no update is needed, will return
   * [FwupDataFetcherError.DownloadError.NoUpdateNeeded]
   */
  suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<FwupData, FwupDataFetcherError>

  sealed class FwupDataFetcherError : Error() {
    data class DownloadError(val error: FirmwareDownloadError) : FwupDataFetcherError()

    data class FileError(val error: Error) : FwupDataFetcherError()

    data class ParseError(val error: ParseFwupManifestError) : FwupDataFetcherError()
  }
}

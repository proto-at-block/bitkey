package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.FirmwareDownloadError.DownloadError
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FirmwareDownloadError.QueryError
import build.wallet.fwup.FirmwareDownloadError.RemoveDirectoryError
import build.wallet.fwup.FirmwareDownloadError.UnzipError
import build.wallet.fwup.FirmwareDownloadError.WriteError
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.memfault.MemfaultClient
import build.wallet.memfault.logMemfaultNetworkFailure
import build.wallet.platform.data.FileManager
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

class FirmwareDownloaderImpl(
  private val memfaultClient: MemfaultClient,
  private val fileManager: FileManager,
) : FirmwareDownloader {
  companion object {
    const val FWUP_BUNDLE_FILENAME = "fwup-bundle.zip"
    const val FWUP_BUNDLE_DIRECTORY = "fwup-bundle"

    // "Software Type" in Memfault is a little confusing.
    //
    // For an individual ELF, the swType is like `dvt-app-b-dev`. But for FWUP, the swType is
    // just 'Dev'.
    //
    // This is necessary because the ELF must disambiguate the slots, and for FWUP the bundle
    // contains all of the ELFs.
    //
    // BTW, you would think the choice should be "Dev" or "Prod" here, but Memfault only allows
    // one software type per hardware revision -- so instead we disambiguate dev vs prod in the
    // hardware revision.
    const val FWUP_SOFTWARE_TYPE = "Dev"
  }

  override suspend fun download(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<Unit, FirmwareDownloadError> {
    return coroutineBinding {
      // Get the latest bundle URL, if there is one
      val fwupBundleUrl =
        memfaultClient.queryForFwupBundle(
          deviceSerial = deviceInfo.serial,
          hardwareVersion = deviceInfo.fwupHwVersion(),
          softwareType = FWUP_SOFTWARE_TYPE,
          currentVersion = deviceInfo.version
        )
          .logMemfaultNetworkFailure { "Failed to query for firmware" }
          .mapError { QueryError(it) }
          .map { it.bundleUrl }
          // If the URL comes back as null, no update is needed
          .toErrorIfNull { NoUpdateNeeded }
          .bind()

      // If we got a URL, use it to download the firmware bundle
      val downloadResult =
        memfaultClient.downloadFwupBundle(fwupBundleUrl)
          .logMemfaultNetworkFailure { "Failed to download firmware" }
          .mapError { DownloadError(it) }
          .bind()

      log(Info) { "Downloaded FWUP bundle" }

      // Persist and unzip the firmware bundle contents
      fileManager.writeFile(downloadResult.bundleZip.toByteArray(), FWUP_BUNDLE_FILENAME)
        .result
        .logFailure { "Failed to write fwup bundle" }
        .mapError { WriteError(it) }
        .bind()

      // Ensure the directory is clean
      fileManager.removeDir(FWUP_BUNDLE_DIRECTORY)
        .result
        .logFailure { "Failed to remove existing fwup bundle" }
        .mapError { RemoveDirectoryError(it) }
        .bind()

      fileManager.unzipFile(FWUP_BUNDLE_FILENAME, FWUP_BUNDLE_DIRECTORY)
        .result
        .logFailure { "Failed to unzip fwup bundle" }
        .mapError { UnzipError(it) }
        .bind()

      Ok(Unit)
    }
  }
}

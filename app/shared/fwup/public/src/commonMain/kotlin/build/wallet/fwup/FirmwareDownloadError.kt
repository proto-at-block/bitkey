package build.wallet.fwup

sealed class FirmwareDownloadError : Error() {
  /**
   * There was an error connecting to memfault to query for the firmware URL
   */
  data class QueryError(override val cause: Throwable) : FirmwareDownloadError()

  /**
   * There was an error connecting to memfault to download the firmware bundle
   * at the given URL after querying
   */
  data class DownloadError(override val cause: Throwable) : FirmwareDownloadError()

  /**
   * Memfault returned a null URL because there is no update needed.
   */
  data object NoUpdateNeeded : FirmwareDownloadError()

  /**
   * There was an error writing the zipped firmware bundle to the file system
   */
  data class WriteError(override val cause: Throwable) : FirmwareDownloadError()

  /**
   * There was an error unzipping the firmware bundle.
   */
  data class UnzipError(override val cause: Throwable) : FirmwareDownloadError()

  /**
   * There was an error removing the existing firmware bundle.
   */
  data class RemoveDirectoryError(override val cause: Throwable) : FirmwareDownloadError()

  /**
   * There was an error calling memfault to move cohorts.
   */
  data class MovingCohortsError(override val cause: Throwable) : FirmwareDownloadError()
}

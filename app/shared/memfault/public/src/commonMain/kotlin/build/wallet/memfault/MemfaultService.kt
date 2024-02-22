package build.wallet.memfault

import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.config.AppId
import com.github.michaelbull.result.Result
import okio.ByteString

interface MemfaultService {
  enum class Cohort {
    DEFAULT,
    BITKEY_TEAM, // Bitkey team members
    BITKEY_EXTERNAL_BETA, // The slug is `bitkey-external-beta`, but really this is all external customers. Naming is hard.
    ;

    fun nameId(): String {
      return name.lowercase().replace("_", "-")
    }

    companion object {
      fun fromAppId(appId: AppId): Cohort {
        return when (appId.value) {
          "world.bitkey" -> BITKEY_EXTERNAL_BETA
          else -> BITKEY_TEAM
        }
      }
    }
  }

  /**
   * Query to see if a FWUP bundle is available. If so, returns a URL which can be passed to
   * `downloadFwupBundle`.
   */
  suspend fun queryForFwupBundle(
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    currentVersion: String,
  ): Result<QueryFwupBundleSuccess, NetworkingError>

  /**
   * Download a FWUP bundle, returning a .zip as a ByteArray.
   */
  suspend fun downloadFwupBundle(url: String): Result<DownloadFwupBundleSuccess, NetworkingError>

  /**
   * Upload a single firmware telemetry event to Memfault. The event must be a
   * Memfault chunk (https://docs.memfault.com/docs/mcu/data-from-firmware-to-the-cloud/).
   * See: https://api-docs.memfault.com/#66b0e390-2c3e-4c0d-b6c2-836a287b9e5f.
   */
  suspend fun uploadTelemetryEvent(
    chunk: ByteArray,
    deviceSerial: String,
  ): Result<UploadTelemetryEventSuccess, NetworkingError>

  /** Upload a coredump to Memfault. */
  suspend fun uploadCoredump(
    coredump: ByteString,
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    softwareVersion: String,
  ): Result<Unit, NetworkingError>

  data class QueryFwupBundleSuccess(val bundleUrl: String?)

  data class DownloadFwupBundleSuccess(val bundleZip: ByteString)

  data class UploadTelemetryEventsSuccess(val remainingChunks: List<ByteArray>)

  data object UploadTelemetryEventSuccess
}

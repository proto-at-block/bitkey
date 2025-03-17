package build.wallet.memfault

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.ktor.result.NetworkingError
import build.wallet.memfault.MemfaultClient.DownloadFwupBundleSuccess
import build.wallet.memfault.MemfaultClient.QueryFwupBundleSuccess
import build.wallet.memfault.MemfaultClient.UploadTelemetryEventSuccess
import com.github.michaelbull.result.Result
import okio.ByteString

class MemfaultClientMock(
  turbine: (String) -> Turbine<Any>,
) : MemfaultClient {
  val uploadCoredumpCalls = turbine("MemfaultService:uploadCoreDump")
  lateinit var uploadCoredumpReturns: List<Result<Unit, NetworkingError>>
  var uploadCoredumpReturnsCount = 0

  val uploadTelemetryEventCalls = turbine("MemfaultService:uploadTelemetryEvent")
  lateinit var uploadTelemetryEventReturns:
    List<Result<UploadTelemetryEventSuccess, NetworkingError>>
  var uploadTelemetryEventsCount = 0

  override suspend fun queryForFwupBundle(
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    currentVersion: String,
  ): Result<QueryFwupBundleSuccess, NetworkingError> {
    TODO("Not yet implemented")
  }

  override suspend fun downloadFwupBundle(
    url: String,
  ): Result<DownloadFwupBundleSuccess, NetworkingError> {
    TODO("Not yet implemented")
  }

  override suspend fun uploadTelemetryEvent(
    chunk: ByteArray,
    deviceSerial: String,
  ): Result<UploadTelemetryEventSuccess, NetworkingError> {
    uploadTelemetryEventCalls += deviceSerial
    return uploadTelemetryEventReturns[uploadTelemetryEventsCount++]
  }

  override suspend fun uploadCoredump(
    coredump: ByteString,
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    softwareVersion: String,
  ): Result<Unit, NetworkingError> {
    uploadCoredumpCalls += deviceSerial
    return uploadCoredumpReturns[uploadCoredumpReturnsCount++]
  }

  fun reset() {
    uploadCoredumpReturnsCount = 0
    uploadTelemetryEventsCount = 0
  }
}

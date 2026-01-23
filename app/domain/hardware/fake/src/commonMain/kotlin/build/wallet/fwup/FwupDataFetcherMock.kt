package build.wallet.fwup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FwupDataFetcherMock(
  private val turbine: (String) -> Turbine<FetchLatestFwupDataParams>,
) : FwupDataFetcher {
  var fetchLatestFwupDataResult: Result<List<McuFwupData>, FwupDataFetcherError> = Ok(
    listOf(McuFwupDataMock)
  )
  var fetchLatestFwupDataCalls = turbine("fetchLatestFwupData calls")

  override suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<List<McuFwupData>, FwupDataFetcherError> {
    fetchLatestFwupDataCalls += FetchLatestFwupDataParams(deviceInfo)
    return fetchLatestFwupDataResult
  }

  fun reset(testName: String) {
    fetchLatestFwupDataCalls = turbine("fetchLatestFwupData calls for $testName")
  }
}

data class FetchLatestFwupDataParams(
  val info: FirmwareDeviceInfo,
)

val McuFwupDataMock = McuFwupData(
  mcuRole = McuRole.CORE,
  mcuName = McuName.EFR32,
  version = FwupDataMock.version,
  chunkSize = FwupDataMock.chunkSize,
  signatureOffset = FwupDataMock.signatureOffset,
  appPropertiesOffset = FwupDataMock.appPropertiesOffset,
  firmware = FwupDataMock.firmware,
  signature = FwupDataMock.signature,
  fwupMode = FwupDataMock.fwupMode
)

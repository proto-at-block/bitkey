package build.wallet.fwup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FwupDataFetcherMock(
  private val turbine: (String) -> Turbine<FetchLatestFwupDataParams>,
) : FwupDataFetcher {
  var fetchLatestFwupDataResult: Result<FwupData, FwupDataFetcherError> = Ok(FwupDataMock)
  var fetchLatestFwupDataCalls = turbine("fetchLatestFwupData calls")

  override suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<FwupData, FwupDataFetcherError> {
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

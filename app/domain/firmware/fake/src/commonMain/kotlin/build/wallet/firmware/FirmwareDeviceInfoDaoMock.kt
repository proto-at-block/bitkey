package build.wallet.firmware

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FirmwareDeviceInfoDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FirmwareDeviceInfoDao {
  val clearCalls = turbine("clear fw device identifiers calls")

  private val deviceIdentifiersFlow = MutableStateFlow<Result<FirmwareDeviceInfo?, Error>>(Ok(null))

  override suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, Error> {
    deviceIdentifiersFlow.value = Ok(deviceInfo)
    return Ok(Unit)
  }

  override fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, Error>> {
    return deviceIdentifiersFlow
  }

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, Error> {
    return deviceIdentifiersFlow.value
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls += Unit
    deviceIdentifiersFlow.value = Ok(null)
    return Ok(Unit)
  }

  fun reset() {
    deviceIdentifiersFlow.value = Ok(null)
  }
}

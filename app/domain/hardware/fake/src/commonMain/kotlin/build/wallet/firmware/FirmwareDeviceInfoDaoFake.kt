package build.wallet.firmware

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FirmwareDeviceInfoDaoFake : FirmwareDeviceInfoDao {
  var storedDeviceInfo: FirmwareDeviceInfo? = null

  override suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, Error> {
    storedDeviceInfo = deviceInfo
    return Ok(Unit)
  }

  override fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, Error>> = flowOf(Ok(storedDeviceInfo))

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, Error> = Ok(storedDeviceInfo)

  override suspend fun clear(): Result<Unit, Error> {
    storedDeviceInfo = null
    return Ok(Unit)
  }

  fun reset() {
    storedDeviceInfo = null
  }
}

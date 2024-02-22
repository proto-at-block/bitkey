package build.wallet.firmware

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FirmwareDeviceInfoDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FirmwareDeviceInfoDao {
  val clearCalls = turbine("clear fw device identifiers calls")

  private var getDeviceIdentifiersResult = Ok<FirmwareDeviceInfo?>(null)

  override suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, DbError> {
    getDeviceIdentifiersResult = Ok(deviceInfo)
    return Ok(Unit)
  }

  override fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, DbError>> {
    return flowOf(getDeviceIdentifiersResult)
  }

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, DbError> {
    return getDeviceIdentifiersResult
  }

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    getDeviceIdentifiersResult = Ok(null)
    return Ok(Unit)
  }

  fun reset() {
    getDeviceIdentifiersResult = Ok(null)
  }
}

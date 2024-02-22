package build.wallet.firmware

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FirmwareDeviceInfoDao {
  /** Set [FirmwareDeviceInfo] */
  suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, DbError>

  /** Return a flow of the fetches of device info */
  fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, DbError>>

  /** Return [FirmwareDeviceInfo]. */
  suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, DbError>

  /** Remove [FirmwareDeviceInfo]. */
  suspend fun clear(): Result<Unit, DbError>
}

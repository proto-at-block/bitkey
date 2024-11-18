package build.wallet.firmware

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FirmwareDeviceInfoDao {
  /** Set [FirmwareDeviceInfo] */
  suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, Error>

  /** Return a flow of the fetches of device info */
  fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, Error>>

  /** Return [FirmwareDeviceInfo]. */
  suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, Error>

  /** Remove [FirmwareDeviceInfo]. */
  suspend fun clear(): Result<Unit, Error>
}

package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FirmwareDeviceInfoDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FirmwareDeviceInfoDao {
  private val database by lazy { databaseProvider.database() }

  override suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, DbError> {
    return database.awaitTransaction {
      firmwareDeviceInfoQueries.setDeviceInfo(
        version = deviceInfo.version,
        serial = deviceInfo.serial,
        swType = deviceInfo.swType,
        hwRevision = deviceInfo.hwRevision,
        activeSlot = deviceInfo.activeSlot,
        batteryCharge = deviceInfo.batteryCharge,
        vCell = deviceInfo.vCell,
        avgCurrentMa = deviceInfo.avgCurrentMa,
        batteryCycles = deviceInfo.batteryCycles,
        secureBootConfig = deviceInfo.secureBootConfig,
        timeRetrieved = deviceInfo.timeRetrieved
      )
    }
      .logFailure { "Failed to set device info" }
  }

  override fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, DbError>> {
    return database.firmwareDeviceInfoQueries.getDeviceInfo()
      .asFlowOfOneOrNull()
      .map { result ->
        result
          .map { deviceInfoEntity ->
            deviceInfoEntity?.let { deviceInfo ->
              FirmwareDeviceInfo(
                version = deviceInfo.version,
                serial = deviceInfo.serial,
                swType = deviceInfo.swType,
                hwRevision = deviceInfo.hwRevision,
                activeSlot = deviceInfo.activeSlot,
                batteryCharge = deviceInfo.batteryCharge,
                vCell = deviceInfo.vCell,
                avgCurrentMa = deviceInfo.avgCurrentMa,
                batteryCycles = deviceInfo.batteryCycles,
                secureBootConfig = deviceInfo.secureBootConfig,
                timeRetrieved = deviceInfo.timeRetrieved
              )
            }
          }
      }
      .distinctUntilChanged()
  }

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, DbError> {
    return database.firmwareDeviceInfoQueries
      .getDeviceInfo()
      .awaitAsOneOrNullResult()
      .logFailure { "Failed to get device info" }
      .map { deviceInfoEntity ->
        deviceInfoEntity?.let { deviceInfo ->
          FirmwareDeviceInfo(
            version = deviceInfo.version,
            serial = deviceInfo.serial,
            swType = deviceInfo.swType,
            hwRevision = deviceInfo.hwRevision,
            activeSlot = deviceInfo.activeSlot,
            batteryCharge = deviceInfo.batteryCharge,
            vCell = deviceInfo.vCell,
            avgCurrentMa = deviceInfo.avgCurrentMa,
            batteryCycles = deviceInfo.batteryCycles,
            secureBootConfig = deviceInfo.secureBootConfig,
            timeRetrieved = deviceInfo.timeRetrieved
          )
        }
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return database
      .awaitTransaction { firmwareDeviceInfoQueries.clear() }
      .logFailure { "Failed to clear device info" }
  }
}

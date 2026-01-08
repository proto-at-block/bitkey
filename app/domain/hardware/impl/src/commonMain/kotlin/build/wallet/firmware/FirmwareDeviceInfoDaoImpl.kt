package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FirmwareDeviceInfoEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class FirmwareDeviceInfoDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FirmwareDeviceInfoDao {
  override suspend fun setDeviceInfo(deviceInfo: FirmwareDeviceInfo): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
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
      mcuInfoDeviceQueries.clear()
      deviceInfo.mcuInfo.forEach { mcuInfo ->
        mcuInfoDeviceQueries.setMcuInfo(
          mcuRole = mcuInfo.mcuRole,
          mcuName = mcuInfo.mcuName,
          firmwareVersion = mcuInfo.firmwareVersion
        )
      }
    }
      .logFailure { "Failed to set device info" }
  }

  override fun deviceInfo(): Flow<Result<FirmwareDeviceInfo?, DbError>> {
    return flow {
      databaseProvider.database()
        .firmwareDeviceInfoQueries
        .getDeviceInfo()
        .asFlowOfOneOrNull()
        .map { result -> result.map { deviceInfoEntity -> map(deviceInfoEntity) } }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, DbError> {
    return databaseProvider.database().firmwareDeviceInfoQueries
      .getDeviceInfo()
      .awaitAsOneOrNullResult()
      .logFailure { "Failed to get device info" }
      .map { deviceInfoEntity -> map(deviceInfoEntity) }
  }

  private suspend fun map(deviceInfoEntity: FirmwareDeviceInfoEntity?): FirmwareDeviceInfo? =
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
        timeRetrieved = deviceInfo.timeRetrieved,
        bioMatchStats = null,
        mcuInfo = findMcuInfo(deviceInfo.rowId)
      )
    }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        mcuInfoDeviceQueries.clear()
        firmwareDeviceInfoQueries.clear()
      }
      .logFailure { "Failed to clear device info" }
  }

  private suspend fun findMcuInfo(rowId: Long): List<McuInfo> =
    databaseProvider.database()
      .mcuInfoDeviceQueries
      .findMcuInfo(rowId)
      .executeAsList()
      .map { mcuInfo ->
        McuInfo(
          mcuRole = mcuInfo.mcuRole,
          mcuName = mcuInfo.mcuName,
          firmwareVersion = mcuInfo.firmwareVersion
        )
      }
}

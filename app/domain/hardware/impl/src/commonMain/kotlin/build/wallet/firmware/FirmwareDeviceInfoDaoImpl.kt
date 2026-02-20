package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FirmwareDeviceInfoEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest

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
        .distinctUntilChanged()
        .mapLatest { result -> result.flatMap { deviceInfoEntity -> map(deviceInfoEntity) } }
        .collect(::emit)
    }
  }

  override suspend fun getDeviceInfo(): Result<FirmwareDeviceInfo?, DbError> {
    return databaseProvider.database().firmwareDeviceInfoQueries
      .getDeviceInfo()
      .awaitAsOneOrNullResult()
      .logFailure { "Failed to get device info" }
      .flatMap { deviceInfoEntity -> map(deviceInfoEntity) }
  }

  private suspend fun map(
    deviceInfoEntity: FirmwareDeviceInfoEntity?,
  ): Result<FirmwareDeviceInfo?, DbError> =
    coroutineBinding {
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
          mcuInfo = findMcuInfo(deviceInfo.rowId).bind()
        )
      }
    }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        mcuInfoDeviceQueries.clear()
        firmwareDeviceInfoQueries.clear()
      }
      .logFailure { "Failed to clear device info" }
  }

  private suspend fun findMcuInfo(rowId: Long): Result<List<McuInfo>, DbError> =
    databaseProvider.database()
      .mcuInfoDeviceQueries
      .findMcuInfo(rowId)
      .awaitAsListResult()
      .map { list ->
        list.map { mcuInfo ->
          McuInfo(
            mcuRole = mcuInfo.mcuRole,
            mcuName = mcuInfo.mcuName,
            firmwareVersion = mcuInfo.firmwareVersion
          )
        }
      }
}

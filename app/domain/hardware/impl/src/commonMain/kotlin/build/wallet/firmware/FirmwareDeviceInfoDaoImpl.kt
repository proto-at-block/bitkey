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
      // Cached firmware update data is only valid for the device it was fetched for: a delta
      // patch is built against a specific device's firmware version and active slot. When the
      // paired hardware changes (notably a W1 -> W3 upgrade) that cache must not survive to be
      // offered to the new device, or the app will retry an update the device always rejects.
      //
      // This is done here, in the same transaction as the write, because this is the single
      // point where the identity of the paired device changes. Doing it here means there is no
      // window in which the persisted device info is new but the cached firmware is old, and it
      // covers every caller -- the W3 upgrade flow, cloud backup restoration, and the
      // telemetry interceptor that runs on nearly every NFC tap.
      val previousSerial = firmwareDeviceInfoQueries.getDeviceInfo().executeAsOneOrNull()?.serial
      if (previousSerial != null && previousSerial != deviceInfo.serial) {
        fwupDataQueries.clearAllMcuFwupData()
        // Sequence IDs track progress through the firmware just discarded; leaving them would
        // resume the new device's transfer partway through.
        fwupDataQueries.clearAllMcuStates()
      }

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
          firmwareVersion = mcuInfo.firmwareVersion,
          activeSlot = mcuInfo.activeSlot
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
        // Forgetting the paired device is an identity change like any other: firmware cached
        // for it is no longer attributable to anything. Leaving it would let the next device
        // paired inherit the previous device's update (wiping hardware clears device info
        // without clearing this cache).
        fwupDataQueries.clearAllMcuFwupData()
        fwupDataQueries.clearAllMcuStates()
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
            firmwareVersion = mcuInfo.firmwareVersion,
            activeSlot = mcuInfo.activeSlot
          )
        }
      }
}

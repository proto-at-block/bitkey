package build.wallet.fwup

import build.wallet.analytics.events.AppSessionManager
import build.wallet.debug.DebugOptionsService
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class FirmwareDataServiceImpl(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fwupDataFetcher: FwupDataFetcher,
  private val fwupDataDao: FwupDataDao,
  private val appSessionManager: AppSessionManager,
  private val debugOptionsService: DebugOptionsService,
) : FirmwareDataService, FirmwareDataSyncWorker {
  private val isHardwareFakeCache = MutableStateFlow(false)
  private val internalSyncFlow = MutableStateFlow(Unit)

  private val firmwareData = MutableStateFlow(FirmwareData(UpToDate, null))

  override suspend fun executeWork() {
    coroutineScope {
      launch {
        debugOptionsService.options()
          .collectLatest { options ->
            isHardwareFakeCache.emit(options.isHardwareFake)
          }
      }

      // We want to check for new firmware when we first load the model
      // and then once every hour after that
      val checkForNewFirmwareFrequency = 1.hours
      launch {
        combine(
          isHardwareFakeCache,
          syncTicker(delay = checkForNewFirmwareFrequency),
          firmwareDeviceInfoDao.deviceInfo()
        ) { isHardwareFake, _, _ ->
          if (!isHardwareFake) {
            checkForNewFwupData()
          }
        }.collect(internalSyncFlow)
      }

      // Maintain a cache of the most recent firmware data
      launch {
        combine(
          firmwareDeviceInfoDao.deviceInfo(),
          fwupDataDao.fwupData(),
          isHardwareFakeCache
        ) { firmwareDeviceInfoResult, fwupDataResult, isHardwareFake ->
          if (isHardwareFake) {
            fakeFirmwareData
          } else {
            val firmwareDeviceInfo = firmwareDeviceInfoResult.get()
            val fwupData = fwupDataResult.get()
            FirmwareData(
              firmwareUpdateState =
                when (fwupData) {
                  null -> UpToDate
                  else -> PendingUpdate(fwupData = fwupData)
                },
              firmwareDeviceInfo = firmwareDeviceInfo
            )
          }
        }.collect(firmwareData)
      }
    }
  }

  private fun syncTicker(delay: Duration) =
    flow {
      while (currentCoroutineContext().isActive) {
        if (appSessionManager.isAppForegrounded()) {
          emit(Unit)
        }
        delay(delay)
      }
    }

  override suspend fun updateFirmwareVersion(fwupData: FwupData): Result<Unit, Error> {
    if (isHardwareFakeCache.value) {
      return Ok(Unit)
    }

    return coroutineBinding {
      val firmwareDeviceInfo = firmwareDeviceInfoDao.getDeviceInfo().get()
      if (firmwareDeviceInfo != null) {
        firmwareDeviceInfoDao.setDeviceInfo(firmwareDeviceInfo.copy(version = fwupData.version))
          .bind()
      } else {
        log { "Firmware device info null after fwup. This should not happen" }
      }

      fwupDataDao.clear().bind()
    }
  }

  override fun firmwareData() = firmwareData

  override suspend fun syncLatestFwupData(): Result<Unit, Error> {
    if (isHardwareFakeCache.value) {
      return Ok(Unit)
    }

    return checkForNewFwupData()
  }

  private suspend fun checkForNewFwupData(): Result<Unit, Error> {
    return coroutineBinding {
      val firmwareDeviceInfo = firmwareDeviceInfoDao.getDeviceInfo().bind()
      if (firmwareDeviceInfo != null) {
        // Get and store new FWUP data, if any.
        val fwupData =
          fwupDataFetcher
            .fetchLatestFwupData(
              deviceInfo = firmwareDeviceInfo
            )
            // If no update needed, return null
            .recoverIf(
              predicate = { error ->
                error is DownloadError && error.error is NoUpdateNeeded
              },
              transform = { null }
            )
            .bind()

        when (fwupData) {
          // No update needed, clear anything in [FwupDataDao] just in case there's something there
          null -> fwupDataDao.clear().bind()
          // There's an update, store it locally to be ready to apply to the HW
          else -> fwupDataDao.setFwupData(fwupData).bind()
        }
      }
    }
      .logFailure(LogLevel.Warn) { "Check for new firmware failed" }
  }

  companion object {
    val fakeFirmwareData = FirmwareData(
      firmwareUpdateState = UpToDate,
      firmwareDeviceInfo =
        FirmwareDeviceInfo(
          version = "1.0.0",
          serial = "fake-hw-serial",
          swType = "dunno",
          hwRevision = "fake-hw-revision",
          activeSlot = FirmwareMetadata.FirmwareSlot.B,
          batteryCharge = 1.234,
          vCell = 1234,
          avgCurrentMa = 1234,
          batteryCycles = 1234,
          secureBootConfig = SecureBootConfig.PROD,
          timeRetrieved = Clock.System.now().epochSeconds,
          bioMatchStats = null
        )
    )
  }
}
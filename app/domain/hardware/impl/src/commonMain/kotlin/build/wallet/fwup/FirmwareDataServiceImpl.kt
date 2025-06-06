package build.wallet.fwup

import build.wallet.coroutines.flow.tickerFlow
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.logging.LogLevel
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.nfc.FakeFirmwareDeviceInfo
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class FirmwareDataServiceImpl(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fwupDataFetcher: FwupDataFetcher,
  private val fwupDataDaoProvider: FwupDataDaoProvider,
  private val appSessionManager: AppSessionManager,
  private val firmwareUpdateSyncFrequency: FirmwareUpdateSyncFrequency,
) : FirmwareDataService, FirmwareDataSyncWorker {
  private val internalSyncFlow = MutableStateFlow(Unit)

  private val firmwareData = MutableStateFlow(FirmwareData(UpToDate, null))

  override suspend fun executeWork() {
    coroutineScope {
      val syncTicker = tickerFlow(firmwareUpdateSyncFrequency.value)
        .filter { appSessionManager.isAppForegrounded() }
      launch {
        combine(
          syncTicker,
          firmwareDeviceInfoDao.deviceInfo()
        ) { _, _ ->
          syncLatestFwupData()
        }.collect { result ->
          if (result.isOk) {
            internalSyncFlow.emit(Unit)
          }
        }
      }

      // Maintain a cache of the most recent firmware data
      launch {
        combine(
          firmwareDeviceInfoDao.deviceInfo(),
          fwupDataDaoProvider.get().fwupData()
        ) { firmwareDeviceInfoResult, fwupDataResult ->
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
        }.collect(firmwareData)
      }
    }
  }

  override suspend fun updateFirmwareVersion(fwupData: FwupData): Result<Unit, Error> {
    return coroutineBinding {
      val firmwareDeviceInfo = firmwareDeviceInfoDao.getDeviceInfo().get()
      if (firmwareDeviceInfo != null) {
        firmwareDeviceInfoDao.setDeviceInfo(firmwareDeviceInfo.copy(version = fwupData.version))
          .bind()
      } else {
        logError { "Firmware device info null after fwup. This should not happen" }
      }

      fwupDataDaoProvider.get().clear().bind()
    }
  }

  override fun firmwareData() = firmwareData

  override suspend fun syncLatestFwupData(): Result<Unit, Error> {
    return coroutineBinding {
      val firmwareDeviceInfo = firmwareDeviceInfoDao.getDeviceInfo().bind()
      if (firmwareDeviceInfo != null && firmwareDeviceInfo.serial != FakeFirmwareDeviceInfo.serial) {
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
          null -> fwupDataDaoProvider.get().clear().bind()
          // There's an update, store it locally to be ready to apply to the HW
          else -> fwupDataDaoProvider.get().setFwupData(fwupData).bind()
        }
      }
    }
      .logFailure(LogLevel.Warn) { "Check for new firmware failed" }
  }
}

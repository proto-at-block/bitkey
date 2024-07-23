package build.wallet.statemachine.data.firmware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupData
import build.wallet.fwup.FwupDataDao
import build.wallet.fwup.FwupDataFetcher
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

class FirmwareDataStateMachineImpl(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fwupDataFetcher: FwupDataFetcher,
  private val fwupDataDao: FwupDataDao,
) : FirmwareDataStateMachine {
  @Composable
  override fun model(props: FirmwareDataProps): FirmwareData {
    if (props.isHardwareFake) {
      return getFakeFirmwareData()
    }

    val firmwareDeviceInfo = rememberFirmwareDeviceInfo()

    val scope = rememberStableCoroutineScope()

    // We want to check for new firmware when we first load the model
    // and then once every hour after that
    val checkForNewFirmwareFrequency = 1.hours
    LaunchedEffect("check-for-new-fw", firmwareDeviceInfo) {
      while (firmwareDeviceInfo != null) {
        checkForNewFirmware(firmwareDeviceInfo)
        delay(checkForNewFirmwareFrequency)
      }
    }

    val pendingFwupData = rememberFwupData()

    return FirmwareData(
      firmwareUpdateState =
        when (pendingFwupData) {
          null -> UpToDate
          else ->
            PendingUpdate(
              fwupData = pendingFwupData,
              onUpdateComplete = {
                scope.launch {
                  // manually update the version of the hardware to the update version
                  when (firmwareDeviceInfo) {
                    null -> log { "Firmware device info null after fwup. This should not happen" }
                    else -> setDeviceInfo(firmwareDeviceInfo, pendingFwupData.version)
                  }

                  clearPendingFwupData()
                }
              }
            )
        },
      firmwareDeviceInfo = firmwareDeviceInfo,
      // We also provide the check here so we can call it on-demand from the UI
      // (like when the Settings screen is shown)
      checkForNewFirmware = {
        scope.launch {
          firmwareDeviceInfo?.let { checkForNewFirmware(it) }
        }
      }
    )
  }

  @Composable
  private fun rememberFwupData(): FwupData? {
    return remember { fwupDataDao.fwupData().map { it.get() } }
      .collectAsState(null).value
  }

  @Composable
  private fun rememberFirmwareDeviceInfo(): FirmwareDeviceInfo? {
    return remember { firmwareDeviceInfoDao.deviceInfo().map { it.get() } }
      .collectAsState(null).value
  }

  private suspend fun clearPendingFwupData() {
    fwupDataDao.clear()
  }

  private suspend fun setDeviceInfo(
    firmwareDeviceInfo: FirmwareDeviceInfo,
    newVersion: String,
  ) {
    firmwareDeviceInfoDao.setDeviceInfo(firmwareDeviceInfo.copy(version = newVersion))
  }

  private suspend fun checkForNewFirmware(firmwareDeviceInfo: FirmwareDeviceInfo) {
    val result =
      coroutineBinding {
        // Get and store new FWUP data, if any.
        val fwupData =
          fwupDataFetcher
            .fetchLatestFwupData(
              deviceInfo = firmwareDeviceInfo
            )
            // If no update needed, return null
            .recoverIf(
              predicate = {
                  error ->
                error is DownloadError && error.error is NoUpdateNeeded
              },
              transform = { null }
            )
            .bind()

        when (fwupData) {
          // No update needed, clear anything in [FwupDataDao] just in case there's something there
          null -> fwupDataDao.clear().bind()
          // There's an update, store it locally to be ready to apply to the HW
          else -> {
            fwupDataDao.setFwupData(fwupData).bind()
          }
        }
      }

    result
      .logFailure(LogLevel.Warn) { "Check for new firmware failed" }
  }

  private fun getFakeFirmwareData(): FirmwareData {
    return FirmwareData(
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
        ),
      checkForNewFirmware = {}
    )
  }
}

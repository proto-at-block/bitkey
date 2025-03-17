package build.wallet.fwup

import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class FirmwareDataServiceFake : FirmwareDataService {
  private val defaultFirmwareData = FirmwareDataUpToDateMock
  val firmwareData = MutableStateFlow(defaultFirmwareData)
  var pendingUpdate: FirmwareData = defaultFirmwareData

  override suspend fun updateFirmwareVersion(fwupData: FwupData): Result<Unit, Error> {
    val currentFirmwareData = firmwareData.value
    val updatedFirmwareData = currentFirmwareData.copy(
      firmwareDeviceInfo = currentFirmwareData.firmwareDeviceInfo
        ?.copy(version = fwupData.version),
      firmwareUpdateState = UpToDate
    )
    firmwareData.value = updatedFirmwareData
    return Ok(Unit)
  }

  override fun firmwareData() = firmwareData

  override suspend fun syncLatestFwupData(): Result<Unit, Error> {
    firmwareData.value = pendingUpdate
    return Ok(Unit)
  }

  fun reset() {
    firmwareData.value = defaultFirmwareData
    pendingUpdate = defaultFirmwareData
  }
}

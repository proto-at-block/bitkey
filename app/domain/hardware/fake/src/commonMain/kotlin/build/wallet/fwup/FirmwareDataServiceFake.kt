package build.wallet.fwup

import build.wallet.db.DbError
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDao
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDaoFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

class FirmwareDataServiceFake(
  private val hardwareProvisionedAppKeyStatusDao: HardwareProvisionedAppKeyStatusDao =
    HardwareProvisionedAppKeyStatusDaoFake(),
) : FirmwareDataService {
  private val defaultFirmwareData = FirmwareDataUpToDateMock
  val firmwareData = MutableStateFlow(defaultFirmwareData)
  var pendingUpdate: FirmwareData = defaultFirmwareData

  override suspend fun updateFirmwareVersion(
    mcuUpdates: ImmutableList<McuFwupData>,
  ): Result<Unit, Error> {
    val currentFirmwareData = firmwareData.value
    // Use first MCU version (for W1, this is the only MCU; for W3, typically CORE)
    val newVersion = mcuUpdates.firstOrNull()?.version
    val updatedFirmwareData = currentFirmwareData.copy(
      firmwareDeviceInfo = newVersion?.let {
        currentFirmwareData.firmwareDeviceInfo?.copy(version = it)
      } ?: currentFirmwareData.firmwareDeviceInfo,
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

  override suspend fun hasProvisionedKey(): Result<Boolean, DbError> {
    return hardwareProvisionedAppKeyStatusDao.isKeyProvisionedForActiveAccount()
  }

  fun reset() {
    firmwareData.value = defaultFirmwareData
    pendingUpdate = defaultFirmwareData
    if (hardwareProvisionedAppKeyStatusDao is HardwareProvisionedAppKeyStatusDaoFake) {
      hardwareProvisionedAppKeyStatusDao.reset()
    }
  }
}

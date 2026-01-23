package build.wallet.fwup

import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate

val FirmwareDataUpToDateMock =
  FirmwareData(
    firmwareUpdateState = UpToDate,
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

/**
 * Mock FirmwareData with W1 pending update (single CORE MCU).
 */
val FirmwareDataPendingUpdateMock =
  FirmwareData(
    firmwareUpdateState =
      PendingUpdate(
        mcuUpdates = immutableListOf(McuFwupDataMock_W1_CORE)
      ),
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

/**
 * Mock FirmwareData with W3 pending update (CORE + UXC MCUs).
 */
val FirmwareDataPendingUpdateMock_W3 =
  FirmwareData(
    firmwareUpdateState =
      PendingUpdate(
        mcuUpdates = immutableListOf(McuFwupDataMock_W3_CORE, McuFwupDataMock_W3_UXC)
      ),
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

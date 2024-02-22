package build.wallet.statemachine.data.firmware

import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FwupDataMock
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate

val FirmwareDataUpToDateMock =
  FirmwareData(
    firmwareUpdateState = UpToDate,
    checkForNewFirmware = {},
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

val FirmwareDataPendingUpdateMock =
  FirmwareData(
    firmwareUpdateState =
      PendingUpdate(
        fwupData = FwupDataMock,
        onUpdateComplete = {}
      ),
    checkForNewFirmware = {},
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

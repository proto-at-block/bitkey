package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate

val FirmwareDataUpToDateMock =
  FirmwareData(
    firmwareUpdateState = UpToDate,
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

val FirmwareDataPendingUpdateMock =
  FirmwareData(
    firmwareUpdateState =
      PendingUpdate(
        fwupData = FwupDataMock
      ),
    firmwareDeviceInfo = FirmwareDeviceInfoMock
  )

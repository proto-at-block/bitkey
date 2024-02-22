package build.wallet.firmware

import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.B

val FirmwareDeviceInfoMock =
  FirmwareDeviceInfo(
    version = "1.2.3",
    serial = "serial",
    swType = "dev",
    hwRevision = "evtd",
    activeSlot = B,
    batteryCharge = 89.45,
    vCell = 1000,
    avgCurrentMa = 2,
    batteryCycles = 91,
    secureBootConfig = SecureBootConfig.PROD,
    timeRetrieved = 1691787589
  )

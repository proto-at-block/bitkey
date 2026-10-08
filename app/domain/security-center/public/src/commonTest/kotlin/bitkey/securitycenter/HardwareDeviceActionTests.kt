package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.B
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class HardwareDeviceActionTests : FunSpec({
  test("W3 descriptor missing is a critical device recommendation") {
    val action = HardwareDeviceAction(
      firmwareData = FirmwareData(UpToDate, w3DeviceInfo()),
      isHardwareDescriptorLoaded = false
    )

    action.getRecommendations().shouldContainExactly(
      SecurityActionRecommendation.PROVISION_HARDWARE_DESCRIPTOR
    )
    action.state().shouldBe(SecurityActionState.HasCriticalActions)
  }

  test("loaded W3 descriptor has no recommendation when firmware is current") {
    val action = HardwareDeviceAction(
      firmwareData = FirmwareData(UpToDate, w3DeviceInfo()),
      isHardwareDescriptorLoaded = true
    )

    action.getRecommendations().shouldContainExactly()
    action.state().shouldBe(SecurityActionState.Secure)
  }
})

private fun w3DeviceInfo() = FirmwareDeviceInfo(
  version = "2.0.0",
  serial = "fake-w3-serial",
  swType = "dev",
  hwRevision = "w3a-core-evt",
  activeSlot = B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 2,
  batteryCycles = 91,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null,
  mcuInfo = emptyList()
)

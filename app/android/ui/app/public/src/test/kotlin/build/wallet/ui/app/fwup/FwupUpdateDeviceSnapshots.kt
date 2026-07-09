package build.wallet.ui.app.fwup

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import bitkey.account.HardwareType
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.fwup.FwupUpdateDeviceModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.app.nfc.FwupInstructionsScreen
import io.kotest.core.spec.style.FunSpec

class FwupUpdateDeviceSnapshots : FunSpec({
  val paparazzi = paparazziExtension()
  val iosDeviceInfo = DeviceInfo(
    deviceModel = "iPhone15,2",
    devicePlatform = DevicePlatform.IOS,
    isEmulator = false
  )

  fun snapshotFwupUpdateDevice(
    hardwareType: HardwareType,
    deviceConfig: DeviceConfig? = null,
  ) {
    paparazzi.snapshot(
      deviceConfig = deviceConfig
    ) {
      CompositionLocalProvider(LocalDeviceInfo provides iosDeviceInfo) {
        val model =
          FwupUpdateDeviceModel(
            devicePlatform = DevicePlatform.IOS,
            hardwareType = hardwareType,
            onClose = {},
            onLaunchFwup = {},
            onReleaseNotes = {},
            bottomSheetModel = null
          ).body as FwupInstructionsBodyModel

        FwupInstructionsScreen(model = model)
      }
    }
  }

  test("fwup update device screen - w3") {
    snapshotFwupUpdateDevice(hardwareType = HardwareType.W3)
  }

  test("fwup update device screen - w3 compact height") {
    snapshotFwupUpdateDevice(
      hardwareType = HardwareType.W3,
      deviceConfig = DeviceConfig.NEXUS_4
    )
  }
})

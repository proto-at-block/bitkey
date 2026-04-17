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
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme
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
    designSystemV2Enabled: Boolean,
    deviceConfig: DeviceConfig? = null,
  ) {
    paparazzi.snapshot(
      designSystemUpdatesEnabled = designSystemV2Enabled,
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

        if (hardwareType == HardwareType.W1) {
          PreviewWalletTheme(
            theme = Theme.DARK,
            designSystemUpdatesEnabled = false
          ) {
            FwupInstructionsScreen(model = model)
          }
        } else {
          FwupInstructionsScreen(model = model)
        }
      }
    }
  }

  test("fwup update device screen - w1") {
    snapshotFwupUpdateDevice(hardwareType = HardwareType.W1, designSystemV2Enabled = false)
  }

  test("fwup update device screen - w1 design system v2") {
    snapshotFwupUpdateDevice(hardwareType = HardwareType.W1, designSystemV2Enabled = true)
  }

  test("fwup update device screen - w3") {
    snapshotFwupUpdateDevice(hardwareType = HardwareType.W3, designSystemV2Enabled = false)
  }

  test("fwup update device screen - w3 design system v2") {
    snapshotFwupUpdateDevice(hardwareType = HardwareType.W3, designSystemV2Enabled = true)
  }

  test("fwup update device screen - w3 design system v2 compact height") {
    snapshotFwupUpdateDevice(
      hardwareType = HardwareType.W3,
      designSystemV2Enabled = true,
      deviceConfig = DeviceConfig.NEXUS_4
    )
  }
})

package build.wallet.statemachine.fwup

import bitkey.account.HardwareType
import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupUpdateDeviceModelTests : FunSpec({
  test("w3 update instructions inherit system theme on iOS") {
    val model = FwupUpdateDeviceModel(
      devicePlatform = DevicePlatform.IOS,
      hardwareType = HardwareType.W3,
      onLaunchFwup = {},
      onClose = {},
      onReleaseNotes = {},
      bottomSheetModel = null
    )

    model.themePreference.shouldBe(ThemePreference.System)
  }

  test("w1 update instructions stay dark on iOS") {
    val model = FwupUpdateDeviceModel(
      devicePlatform = DevicePlatform.IOS,
      hardwareType = HardwareType.W1,
      onLaunchFwup = {},
      onClose = {},
      onReleaseNotes = {},
      bottomSheetModel = null
    )

    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
  }
})

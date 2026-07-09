package build.wallet.statemachine.fwup

import bitkey.account.HardwareType
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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

  test("update instructions can show help accessory") {
    val model = FwupUpdateDeviceModel(
      devicePlatform = DevicePlatform.Android,
      hardwareType = HardwareType.W3,
      onLaunchFwup = {},
      onClose = {},
      onHelpClick = {},
      onReleaseNotes = {},
      bottomSheetModel = null
    )

    val body = model.body.shouldBeInstanceOf<FwupInstructionsBodyModel>()
    body.toolbarModel.trailingAccessory.shouldNotBeNull().shouldBeInstanceOf<IconAccessory>()
  }
})

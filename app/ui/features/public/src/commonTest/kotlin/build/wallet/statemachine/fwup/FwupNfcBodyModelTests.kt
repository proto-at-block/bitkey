package build.wallet.statemachine.fwup

import build.wallet.firmware.McuRole
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.ScreenPresentationStyle.FullScreen
import build.wallet.statemachine.core.ScreenPresentationStyle.ModalFullScreen
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.InProgress
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.LostConnection
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupNfcBodyModelTests : FunSpec({

  // InProgress Status Text Tests

  test("InProgress text shows 'Updating...' for W1 single MCU") {
    val status = InProgress(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 1,
      fwupProgress = 50f
    )
    status.text.shouldBe("Updating...")
  }

  test("InProgress text shows '(1/2)' for W3 first MCU (UXC)") {
    val status = InProgress(
      currentMcuRole = McuRole.UXC,
      mcuIndex = 0,
      totalMcus = 2,
      fwupProgress = 50f
    )
    status.text.shouldBe("Updating (1/2)...")
  }

  test("InProgress text shows '(2/2)' for W3 second MCU (CORE)") {
    val status = InProgress(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 1,
      totalMcus = 2,
      fwupProgress = 75f
    )
    status.text.shouldBe("Updating (2/2)...")
  }

  test("InProgress progressText shows percentage") {
    val status = InProgress(fwupProgress = 45.6f)
    status.progressText.shouldBe("46%")
  }

  test("InProgress progressPercentage is normalized 0-1") {
    val status = InProgress(fwupProgress = 50f)
    status.progressPercentage.shouldBe(0.5f)
  }

  // LostConnection Status Text Tests

  test("LostConnection text for W1 single MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 0,
      totalMcus = 1,
      fwupProgress = 30f
    )
    status.text.shouldBe("Device no longer detected,\nhold device to phone")
  }

  test("LostConnection text shows '(1/2)' for W3 first MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.UXC,
      mcuIndex = 0,
      totalMcus = 2,
      fwupProgress = 30f
    )
    status.text.shouldBe("Lost connection during update (1/2),\nhold device to phone")
  }

  test("LostConnection text shows '(2/2)' for W3 second MCU") {
    val status = LostConnection(
      currentMcuRole = McuRole.CORE,
      mcuIndex = 1,
      totalMcus = 2,
      fwupProgress = 60f
    )
    status.text.shouldBe("Lost connection during update (2/2),\nhold device to phone")
  }

  test("LostConnection progressPercentage is normalized 0-1") {
    val status = LostConnection(fwupProgress = 75f)
    status.progressPercentage.shouldBe(0.75f)
  }

  test("platform FWUP NFC screen inherits system theme on iOS") {
    val model = FwupNfcBodyModel(
      onCancel = null,
      status = FwupNfcBodyModel.Status.Searching(),
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      devicePlatform = DevicePlatform.IOS
    )

    model.presentationStyle.shouldBe(ModalFullScreen)
    model.themePreference.shouldBe(ThemePreference.System)
  }

  test("platform FWUP NFC stays dark on iOS when native sheet is disabled") {
    val model = FwupNfcBodyModel(
      onCancel = null,
      status = FwupNfcBodyModel.Status.Searching(),
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      devicePlatform = DevicePlatform.IOS
    )

    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
  }

  test("platform FWUP NFC screen inherits system theme on Android") {
    val model = FwupNfcBodyModel(
      onCancel = null,
      status = FwupNfcBodyModel.Status.Searching(),
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      devicePlatform = DevicePlatform.Android
    )

    model.themePreference.shouldBe(ThemePreference.System)
  }

  test("full screen FWUP NFC stays dark on iOS") {
    val model = FwupNfcBodyModel(
      onCancel = null,
      status = FwupNfcBodyModel.Status.Searching(),
      eventTrackerScreenInfo = null
    ).asFullScreen()

    model.presentationStyle.shouldBe(FullScreen)
    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
  }
})

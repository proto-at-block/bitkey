package build.wallet.statemachine.nfc

import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NfcBodyModelTests : FunSpec({
  test("platform NFC screen inherits system theme on iOS when dsv2 is enabled") {
    val model = NfcBodyModel(
      text = "Hold your Bitkey to the back of your phone",
      status = NfcBodyModel.Status.Success,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      designSystemV2Enabled = true,
      devicePlatform = DevicePlatform.IOS
    )

    model.themePreference.shouldBe(ThemePreference.System)
  }

  test("platform NFC screen stays dark on iOS when native sheet is disabled") {
    val model = NfcBodyModel(
      text = "Hold your Bitkey to the back of your phone",
      status = NfcBodyModel.Status.Success,
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      designSystemV2Enabled = true,
      devicePlatform = DevicePlatform.IOS
    )

    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
  }

  test("platform NFC screen stays dark on iOS when dsv2 is disabled") {
    val model = NfcBodyModel(
      text = "Hold your Bitkey to the back of your phone",
      status = NfcBodyModel.Status.Success,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      designSystemV2Enabled = false,
      devicePlatform = DevicePlatform.IOS
    )

    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
    model.platformNfcScreen.shouldBe(true)
  }

  test("legacy iOS NFC screen stops preserving the native sheet when disabled") {
    val model = NfcBodyModel(
      text = "Hold your Bitkey to the back of your phone",
      status = NfcBodyModel.Status.Success,
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      designSystemV2Enabled = false,
      devicePlatform = DevicePlatform.IOS
    )

    model.themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
    model.platformNfcScreen.shouldBe(false)
  }

  test("platform NFC screen inherits system theme on Android when dsv2 is enabled") {
    val model = NfcBodyModel(
      text = "Hold your Bitkey to the back of your phone",
      status = NfcBodyModel.Status.Success,
      eventTrackerScreenInfo = null
    ).asPlatformNfcScreen(
      designSystemV2Enabled = true,
      devicePlatform = DevicePlatform.Android
    )

    model.themePreference.shouldBe(ThemePreference.System)
  }

  test("theme helper stays dark on iOS when system follow is disabled") {
    nfcThemePreference(
      designSystemV2Enabled = true,
      devicePlatform = DevicePlatform.IOS,
      followSystemOnIos = false
    ).shouldBe(ThemePreference.Manual(Theme.DARK))
  }
})

package build.wallet.statemachine.nfc

import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

internal fun nfcThemePreference(
  devicePlatform: DevicePlatform,
  followSystemOnIos: Boolean = true,
): ThemePreference =
  when {
    devicePlatform == DevicePlatform.Android -> ThemePreference.System
    followSystemOnIos && devicePlatform == DevicePlatform.IOS -> ThemePreference.System
    else -> ThemePreference.Manual(Theme.DARK)
  }

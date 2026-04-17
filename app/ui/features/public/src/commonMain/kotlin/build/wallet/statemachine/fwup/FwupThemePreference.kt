package build.wallet.statemachine.fwup

import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

internal fun fwupThemePreference(
  devicePlatform: DevicePlatform,
  followSystemOnIos: Boolean = true,
): ThemePreference =
  when (devicePlatform) {
    DevicePlatform.IOS ->
      if (followSystemOnIos) {
        ThemePreference.System
      } else {
        ThemePreference.Manual(Theme.DARK)
      }
    DevicePlatform.Android -> ThemePreference.System
    DevicePlatform.Jvm -> ThemePreference.Manual(Theme.DARK)
  }

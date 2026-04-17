package build.wallet.statemachine.nfc

import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

internal fun nfcThemePreference(
  designSystemV2Enabled: Boolean,
  devicePlatform: DevicePlatform,
  followSystemOnIos: Boolean = true,
): ThemePreference =
  when {
    designSystemV2Enabled && devicePlatform == DevicePlatform.Android -> ThemePreference.System
    designSystemV2Enabled && followSystemOnIos && devicePlatform == DevicePlatform.IOS -> ThemePreference.System
    else -> ThemePreference.Manual(Theme.DARK)
  }

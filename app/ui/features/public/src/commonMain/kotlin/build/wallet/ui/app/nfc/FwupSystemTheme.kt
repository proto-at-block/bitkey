package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.tooling.LocalIsPreviewTheme
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.theme.systemTheme

@Composable
internal fun FwupSystemThemedContent(
  followIosSystemTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val shouldFollowIosSystemTheme =
    followIosSystemTheme &&
      LocalDeviceInfo.current.devicePlatform == DevicePlatform.IOS
  val previewTheme = LocalTheme.current

  if (shouldFollowIosSystemTheme) {
    val theme = if (LocalIsPreviewTheme.current) previewTheme else systemTheme()
    CompositionLocalProvider(LocalTheme provides theme) {
      WalletTheme {
        content()
      }
    }
  } else {
    content()
  }
}

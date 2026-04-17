package build.wallet.ui.app.nfc

import build.wallet.ui.theme.Theme
import platform.Foundation.NSBundle

internal actual fun iosNfcHeroVideoResource(
  video: IosNfcHeroVideo,
  theme: Theme,
): String? {
  val fileName = when (video) {
    IosNfcHeroVideo.Standard -> "ios_nfc_activation_standard"
    IosNfcHeroVideo.StandardW1 -> "ios_nfc_activation_w1"
    IosNfcHeroVideo.Fwup ->
      when (theme) {
        Theme.DARK -> "ios_nfc_activation_fwup"
        Theme.LIGHT -> "ios_nfc_activation_fwup_light"
      }
  }

  return NSBundle.mainBundle
    .URLForResource(name = fileName, withExtension = "mov")
    ?.toString()
}

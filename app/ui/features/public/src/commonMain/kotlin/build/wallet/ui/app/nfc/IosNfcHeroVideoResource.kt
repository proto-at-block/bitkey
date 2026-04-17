package build.wallet.ui.app.nfc

import build.wallet.ui.theme.Theme

internal enum class IosNfcHeroVideo {
  /** Standard NFC activation video for W3 hardware. */
  Standard,

  /** Standard NFC activation video for W1 hardware. */
  StandardW1,

  /** FWUP-specific NFC activation video. */
  Fwup,
}

internal expect fun iosNfcHeroVideoResource(
  video: IosNfcHeroVideo,
  theme: Theme,
): String?

package build.wallet.ui.app.nfc

// Desktop/JVM has no FWUP hero video (see FwupUpdateHeroVideoResource.jvm.kt),
// so the crossfade placeholder is unnecessary: the screen renders the real
// static hero image directly at full opacity.
internal actual val fwupHeroVideoPlaceholderEnabled: Boolean = false

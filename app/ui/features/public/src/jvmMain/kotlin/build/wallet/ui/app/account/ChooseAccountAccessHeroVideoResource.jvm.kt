package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.ui.compose.getVideoResource

/**
 * Returns the shared hero resource identifier so the onboarding screen lays out
 * its video region consistently with the other platforms.
 *
 * On desktop/JVM the resolved identifier is just the file name (see the JVM
 * [Res.getVideoResource]); the [build.wallet.ui.components.video.VideoPlayer]
 * actual cannot decode the bundled hero video and instead renders a tasteful
 * branded background behind the onboarding logo and buttons. W-17310 forbids
 * adding a video dependency, and the hero asset is a true video (not Lottie),
 * so an animated desktop hero is intentionally out of scope.
 */
@Composable
internal actual fun chooseAccountAccessHeroVideoResource(): String {
  return Res.getVideoResource("bitkey_w3_homepage_9x16_150")
}

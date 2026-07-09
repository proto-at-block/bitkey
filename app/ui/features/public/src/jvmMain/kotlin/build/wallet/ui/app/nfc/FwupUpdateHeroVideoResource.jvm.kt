package build.wallet.ui.app.nfc

import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import bitkey.account.HardwareType
import build.wallet.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * Returns `null` on desktop/JVM: the FWUP hero is shipped as a true video file
 * bundled with the iOS/Android apps and is not available to the Compose Desktop
 * host (and W-17310 forbids adding a video dependency). Returning `null` routes
 * the FWUP screen to [FwupUpdateHeroPlatformImage], which renders the real
 * static hero drawable below — a functional desktop experience rather than a
 * blank/animated stub.
 */
@Composable
internal actual fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String? = null

@Composable
internal actual fun FwupUpdateHeroPlatformImage(
  modifier: Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
) {
  Image(
    painter = painterResource(updateFirmwareHeroImageResource(theme, hardwareType)),
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    alignment = Alignment.Center,
    alpha = alpha
  )
}

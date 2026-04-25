package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import bitkey.account.HardwareType
import build.wallet.ui.theme.Theme

@Composable
internal expect fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String?

@Composable
internal expect fun FwupUpdateHeroPlatformImage(
  modifier: Modifier = Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
)

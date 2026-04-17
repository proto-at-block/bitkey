package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import bitkey.account.HardwareType
import build.wallet.ui.theme.Theme

@Composable
internal expect fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String?

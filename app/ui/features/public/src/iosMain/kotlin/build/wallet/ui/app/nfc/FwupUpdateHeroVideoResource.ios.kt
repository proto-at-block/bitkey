package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.theme.Theme

@Composable
internal actual fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String? {
  return Res.getVideoResource(
    when (hardwareType) {
      HardwareType.W1 -> "pair"
      HardwareType.W3 ->
        if (theme == Theme.DARK) {
          "firmware_update_dark"
        } else {
          "firmware_update_light"
        }
    }
  )
}

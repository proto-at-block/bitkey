package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.ui.compose.getVideoResource

@Composable
internal actual fun chooseAccountAccessHeroVideoResource(): String {
  return Res.getVideoResource("bitkey_w3_homepage_9x16_150")
}

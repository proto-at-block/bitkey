package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import bitkey.ui.framework_public.generated.resources.Res

@Composable
actual fun Res.getVideoResource(fileName: String): String {
  return fileName
}

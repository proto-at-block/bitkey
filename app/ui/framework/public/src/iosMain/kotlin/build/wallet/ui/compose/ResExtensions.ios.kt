package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import bitkey.ui.framework_public.generated.resources.Res
import platform.Foundation.NSBundle

@HiddenFromObjC
@Composable
actual fun Res.getVideoResource(fileName: String): String {
  return NSBundle.mainBundle
    .URLForResource(name = fileName, withExtension = "mov")
    ?.toString()
    .orEmpty()
}

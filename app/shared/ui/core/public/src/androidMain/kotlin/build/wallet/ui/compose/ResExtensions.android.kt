package build.wallet.ui.compose

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import bitkey.shared.ui_core_public.generated.resources.Res

@SuppressLint("DiscouragedApi")
@Composable
actual fun Res.getVideoResource(fileName: String): String {
  // NOTE: Identifier by file name must not contain file extension
  val context = LocalContext.current
  return context.resources
    .getIdentifier(fileName, "raw", context.packageName)
    .toString()
}

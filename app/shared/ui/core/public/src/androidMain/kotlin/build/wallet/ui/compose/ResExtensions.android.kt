package build.wallet.ui.compose

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import bitkey.shared.ui_core_public.generated.resources.Res

@SuppressLint("DiscouragedApi")
@Composable
actual fun Res.getVideoResource(fileName: String): String {
  // NOTE: Identifier by file name must not contain file extension
  // NOTE: we are not using CMP resources because CMP resources are files that are
  //       bundled with the app and are not part of the resources directory.
  //       See https://github.com/androidx/media/issues/1405.
  val context = LocalContext.current
  return context.resources
    .getIdentifier(fileName, "raw", context.packageName)
    .toString()
}

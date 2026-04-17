package build.wallet.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun KeepScreenOn() {
  val activity = LocalContext.current.findActivity()
  DisposableEffect(activity) {
    activity?.window?.addFlags(FLAG_KEEP_SCREEN_ON)
    onDispose {
      activity?.window?.clearFlags(FLAG_KEEP_SCREEN_ON)
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

package build.wallet.ui.system

import android.app.Activity
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun KeepScreenOn() {
  val activity = LocalContext.current as Activity
  DisposableEffect("keep-screen-on") {
    val window = activity.window
    window?.addFlags(FLAG_KEEP_SCREEN_ON)
    onDispose {
      window?.clearFlags(FLAG_KEEP_SCREEN_ON)
    }
  }
}

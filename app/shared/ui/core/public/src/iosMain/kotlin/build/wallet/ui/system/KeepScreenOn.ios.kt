package build.wallet.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun KeepScreenOn() {
  DisposableEffect("keep-screen-on") {
    UIApplication.sharedApplication.idleTimerDisabled = true
    onDispose {
      UIApplication.sharedApplication.idleTimerDisabled = false
    }
  }
}

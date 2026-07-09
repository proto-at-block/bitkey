package build.wallet.ui.app.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSNotificationCenter

private const val DYNAMIC_ISLAND_SCANNER_STATUS_BAR_HIDDEN_NOTIFICATION =
  "build.wallet.dynamicIslandScannerStatusBarHiddenChanged"

@Composable
internal actual fun DynamicIslandScannerStatusBarHiddenEffect() {
  DisposableEffect(Unit) {
    postStatusBarHiddenChange(hidden = true)

    onDispose {
      postStatusBarHiddenChange(hidden = false)
    }
  }
}

private fun postStatusBarHiddenChange(hidden: Boolean) {
  NSNotificationCenter.defaultCenter.postNotificationName(
    aName = DYNAMIC_ISLAND_SCANNER_STATUS_BAR_HIDDEN_NOTIFICATION,
    `object` = null,
    userInfo = mapOf("hidden" to hidden)
  )
}

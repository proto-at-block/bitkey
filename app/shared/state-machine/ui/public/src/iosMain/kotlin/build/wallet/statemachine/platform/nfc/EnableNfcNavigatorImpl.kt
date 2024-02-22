package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable

actual class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
    error("Should be unreachable in iOS")
  }
}

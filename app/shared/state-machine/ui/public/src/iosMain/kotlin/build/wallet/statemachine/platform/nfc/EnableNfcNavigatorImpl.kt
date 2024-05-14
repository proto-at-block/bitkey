package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log

actual class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
    LaunchedEffect("log-nfc-is-not-supported") {
      // TODO: W-6657 - figure out why some iOS devices are not supporting NFC.
      log(Error) { "NFC is not available on this iOS device." }
    }
  }
}

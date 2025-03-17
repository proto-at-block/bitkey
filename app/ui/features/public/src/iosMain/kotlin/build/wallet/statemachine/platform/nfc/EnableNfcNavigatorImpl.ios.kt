package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*

@BitkeyInject(AppScope::class)
class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
    LaunchedEffect("log-nfc-is-not-supported") {
      // TODO: W-6657 - figure out why some iOS devices are not supporting NFC.
      logError { "NFC is not available on this iOS device." }
    }
  }
}

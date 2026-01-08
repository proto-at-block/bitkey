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
      // This should never be called on iOS - NFC is either available or the device
      // lacks hardware entirely. There's no "disabled" state like Android.
      logWarn { "NFC is not available on this iOS device." }
    }
  }
}

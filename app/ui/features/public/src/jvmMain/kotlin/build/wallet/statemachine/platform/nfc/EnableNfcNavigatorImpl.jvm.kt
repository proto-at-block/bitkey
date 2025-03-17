package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
    TODO("Implement this for jvm, if necessary.")
  }
}

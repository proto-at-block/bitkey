package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable

class EnableNfcNavigatorMock : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
    // No-op
  }
}

package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable

expect class EnableNfcNavigatorImpl() : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit)
}

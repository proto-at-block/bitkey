package build.wallet.statemachine.platform.nfc

import androidx.compose.runtime.Composable

/**
 * Navigate to the settings screen to enable NFC.
 * This is effectively Android-only and for iOS this is implemented as
 * an unreachable thrown exception.
 */
interface EnableNfcNavigator {
  @Composable
  fun navigateToEnableNfc(onReturn: () -> Unit)
}

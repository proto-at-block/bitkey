package build.wallet.statemachine.platform.nfc

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

actual class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  actual override fun navigateToEnableNfc(onReturn: () -> Unit) {
    val launcher =
      rememberLauncherForActivityResult(
        contract = StartActivityForResult(),
        onResult = {
          onReturn()
        }
      )

    LaunchedEffect("navigate-to-enable-nfc") {
      launcher.launch(Intent(Settings.ACTION_NFC_SETTINGS))
    }
  }
}

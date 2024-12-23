package build.wallet.statemachine.platform.nfc

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject

@BitkeyInject(ActivityScope::class)
class EnableNfcNavigatorImpl : EnableNfcNavigator {
  @Composable
  override fun navigateToEnableNfc(onReturn: () -> Unit) {
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

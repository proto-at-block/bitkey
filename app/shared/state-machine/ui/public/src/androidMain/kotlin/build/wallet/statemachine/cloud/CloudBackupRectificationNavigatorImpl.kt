package build.wallet.statemachine.cloud

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

actual class CloudBackupRectificationNavigatorImpl : CloudBackupRectificationNavigator {
  @SuppressLint("ComposableNaming")
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  ) {
    val launcher =
      rememberLauncherForActivityResult(
        contract = StartActivityForResult(),
        onResult = {
          onReturn()
        }
      )

    LaunchedEffect("navigate-to-enable-nfc") {
      launcher.launch(data as Intent)
    }
  }
}

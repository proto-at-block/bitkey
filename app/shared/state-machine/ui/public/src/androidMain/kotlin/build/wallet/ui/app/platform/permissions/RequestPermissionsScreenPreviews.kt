package build.wallet.ui.app.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.platform.permissions.RequestPermissionBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewRequestPermissionScreen() {
  PreviewWalletTheme {
    RequestPermissionScreen(
      model = RequestPermissionBodyModel(
        title = "Requesting Permission",
        explanation = "This permission is needed in order to use this feature to change the world",
        showingSystemPermission = false,
        onBack = {},
        onRequest = {}
      )
    )
  }
}

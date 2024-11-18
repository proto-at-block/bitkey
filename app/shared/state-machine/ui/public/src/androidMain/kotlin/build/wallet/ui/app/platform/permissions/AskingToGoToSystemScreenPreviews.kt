package build.wallet.ui.app.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.platform.permissions.AskingToGoToSystemBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewAskingToGoToSystemScreen() {
  PreviewWalletTheme {
    AskingToGoToSystemScreen(
      model = AskingToGoToSystemBodyModel(
        title = "Requesting Permission",
        explanation = "This permission is needed in order to use this. Go to system settings or else",
        onBack = {},
        onGoToSetting = {}
      )
    )
  }
}

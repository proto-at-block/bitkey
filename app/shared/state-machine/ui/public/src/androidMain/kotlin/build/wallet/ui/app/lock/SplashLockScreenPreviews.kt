package build.wallet.ui.app.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
@Preview
fun SplashLockScreenPreview() {
  PreviewWalletTheme {
    SplashLockScreen(
      model = SplashLockModel(
        unlockButtonModel = ButtonModel(
          text = "Unlock",
          treatment = ButtonModel.Treatment.Translucent,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {}
        )
      )
    )
  }
}

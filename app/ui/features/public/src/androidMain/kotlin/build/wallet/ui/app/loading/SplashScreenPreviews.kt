package build.wallet.ui.app.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlin.time.Duration.Companion.seconds

@Preview
@Composable
fun PreviewSplashScreen() {
  PreviewWalletTheme {
    SplashScreen(
      model =
        SplashBodyModel(
          bitkeyWordMarkAnimationDelay = 0.seconds,
          bitkeyWordMarkAnimationDuration = 0.seconds
        )
    )
  }
}

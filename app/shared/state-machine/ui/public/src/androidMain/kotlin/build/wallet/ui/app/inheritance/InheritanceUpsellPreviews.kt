package build.wallet.ui.app.inheritance

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.ui.components.screen.Screen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun InheritanceUpsellPreview() {
  PreviewWalletTheme {
    Screen(
      model = ScreenModel(
        body = InheritanceUpsellBodyModel(
          onGetStarted = {},
          onClose = {}
        )
      ),
      modifier = Modifier.fillMaxSize()
    )
  }
}

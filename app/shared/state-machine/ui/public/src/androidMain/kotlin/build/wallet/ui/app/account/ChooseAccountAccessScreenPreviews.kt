package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun ChooseAccountAccessScreenPreview() {
  PreviewWalletTheme {
    ChooseAccountAccessScreen(
      model =
        ChooseAccountAccessModel(
          onLogoClick = {},
          onSetUpNewWalletClick = {},
          onMoreOptionsClick = {}
        )
    )
  }
}

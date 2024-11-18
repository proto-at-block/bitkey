package build.wallet.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun DataRowTotal() {
  PreviewWalletTheme {
    FormMainContentModel.DataList.Data(
      title = "Total cost",
      sideText = "$21.36",
      secondarySideText = "(0.0010 BTC)"
    )
  }
}

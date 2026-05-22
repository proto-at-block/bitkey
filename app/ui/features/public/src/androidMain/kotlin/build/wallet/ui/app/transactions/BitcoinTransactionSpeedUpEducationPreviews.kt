package build.wallet.ui.app.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.transactions.BitcoinTransactionSpeedUpEducationBodyModel
import build.wallet.ui.model.render
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Speed Up Transaction Education")
@Composable
fun BitcoinTransactionSpeedUpEducationPreview() {
  PreviewWalletTheme {
    BitcoinTransactionSpeedUpEducationBodyModel(
      onSpeedUpTransaction = {},
      onClose = {}
    ).render()
  }
}

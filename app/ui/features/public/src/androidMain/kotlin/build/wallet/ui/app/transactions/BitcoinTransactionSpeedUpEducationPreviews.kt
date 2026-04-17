package build.wallet.ui.app.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.transactions.BitcoinTransactionSpeedUpEducationBodyModel
import build.wallet.ui.model.render
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Speed Up Transaction Education")
@Composable
fun BitcoinTransactionSpeedUpEducationPreview() {
  BitcoinTransactionSpeedUpEducationPreview(designSystemUpdatesEnabled = false)
}

@Preview(name = "Speed Up Transaction Education (Design System V2)")
@Composable
fun BitcoinTransactionSpeedUpEducationDesignSystemV2Preview() {
  BitcoinTransactionSpeedUpEducationPreview(designSystemUpdatesEnabled = true)
}

@Composable
private fun BitcoinTransactionSpeedUpEducationPreview(designSystemUpdatesEnabled: Boolean) {
  PreviewWalletTheme(designSystemUpdatesEnabled = designSystemUpdatesEnabled) {
    BitcoinTransactionSpeedUpEducationBodyModel(
      onSpeedUpTransaction = {},
      onClose = {}
    ).render()
  }
}

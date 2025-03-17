package build.wallet.ui.components.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
@Preview
internal fun ListSectionHeaderPreview() {
  PreviewWalletTheme {
    ListHeader(title = "Pending")
  }
}

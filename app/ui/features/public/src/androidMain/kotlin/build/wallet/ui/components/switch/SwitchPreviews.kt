package build.wallet.ui.components.switch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun SwitchPreview() {
  PreviewWalletTheme {
    Column {
      Switch(checked = true, onCheckedChange = {})
      Spacer(Modifier.height(5.dp))
      Switch(checked = false, onCheckedChange = {})
    }
  }
}

package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Preview
@Composable
fun PreviewCard() {
  Box(
    modifier =
      Modifier
        .background(color = WalletTheme.colors.background)
        .padding(24.dp)
  ) {
    Card {
      Spacer(modifier = Modifier.height(8.dp))
      Label(text = "PreviewCard Title", type = LabelType.Title2)
      Label(text = "PreviewCard Body", type = LabelType.Body2Regular)
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

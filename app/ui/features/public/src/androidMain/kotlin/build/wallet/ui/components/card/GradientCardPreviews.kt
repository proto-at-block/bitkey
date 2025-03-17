package build.wallet.ui.components.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter

@Preview
@Composable
internal fun GradientCardPreview() {
  Box(
    modifier =
      Modifier
        .background(color = WalletTheme.colors.background)
        .padding(24.dp)
  ) {
    GradientCard {
      Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Image(
          painter = Icon.SmallIconBitkey.painter(),
          contentDescription = ""
        )
        Spacer(modifier = Modifier.width(12.dp))
        Label(text = "PreviewGradientCard Title", type = LabelType.Title2)
      }
    }
  }
}

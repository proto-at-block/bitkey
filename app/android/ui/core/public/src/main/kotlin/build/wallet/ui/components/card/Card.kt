package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun Card(
  modifier: Modifier = Modifier,
  backgroundColor: Color = WalletTheme.colors.containerBackground,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .background(
          color = backgroundColor,
          shape = RoundedCornerShape(16.dp)
        )
        .border(
          width = 1.dp,
          color = WalletTheme.colors.foreground10,
          shape = RoundedCornerShape(16.dp)
        )
        .padding(paddingValues),
    verticalArrangement = verticalArrangement,
    horizontalAlignment = horizontalAlignment
  ) {
    content()
  }
}

@Preview
@Composable
internal fun PreviewCard() {
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

package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

@Composable
fun Card(
  modifier: Modifier = Modifier,
  backgroundColor: Color = WalletTheme.colors.containerBackground,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  cornerRadius: Dp = 16.dp,
  borderWidth: Dp = 1.dp,
  paddingValues: PaddingValues = PaddingValues(horizontal = 16.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .background(
          color = backgroundColor,
          shape = RoundedCornerShape(cornerRadius)
        )
        .border(
          width = borderWidth,
          color = WalletTheme.colors.foreground10,
          shape = RoundedCornerShape(cornerRadius)
        )
        .padding(paddingValues),
    verticalArrangement = verticalArrangement,
    horizontalAlignment = horizontalAlignment
  ) {
    content()
  }
}

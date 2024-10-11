package build.wallet.ui.components.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun GradientCard(
  modifier: Modifier = Modifier,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  paddingValues: PaddingValues =
    PaddingValues(
      horizontal = 16.dp,
      vertical = 12.dp
    ),
  backgroundColor: Color = Color(0xFFF5F8FE),
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .shadow(
          elevation = 8.dp,
          shape = RoundedCornerShape(size = 16.dp),
          spotColor = Color(0x0A4C555E),
          ambientColor = Color(0x0A4C555E)
        )
        .shadow(
          elevation = 1.dp,
          shape = RoundedCornerShape(size = 16.dp),
          spotColor = Color(0xFFBDCFF0),
          ambientColor = Color(0xFFBDCFF0)
        )
        .background(
          color = backgroundColor,
          shape = RoundedCornerShape(size = 16.dp)
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

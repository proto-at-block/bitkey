package build.wallet.ui.components.card

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp

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

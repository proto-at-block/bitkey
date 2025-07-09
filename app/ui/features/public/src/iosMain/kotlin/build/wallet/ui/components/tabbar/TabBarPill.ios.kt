package build.wallet.ui.components.tabbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

// controls the number of shadow layers
private const val SHADOW_LAYER_COUNT = 6

// controls the distance of each shadow layer from the main pill
private const val OFFSET_MULTIPLIER = 6f

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  tabs: @Composable (() -> Unit),
) {
  Row(
    modifier = modifier
      .height(60.dp)
      .width(130.dp)
      .drawBehind {
        for (i in 1..SHADOW_LAYER_COUNT) {
          val factor = i * OFFSET_MULTIPLIER
          drawRoundRect(
            color = Color.Black.copy(alpha = 0.002f * (7 - i)),
            topLeft = Offset(-factor, -factor),
            size = Size(size.width + 2 * factor, size.height + 2 * factor),
            cornerRadius = CornerRadius(30.dp.toPx() + factor, 30.dp.toPx() + factor)
          )
        }
      }
      .background(WalletTheme.colors.tabBarBackground, shape = RoundedCornerShape(30.dp)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly
  ) {
    tabs()
  }
}

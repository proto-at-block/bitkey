package build.wallet.ui.components.tabbar

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  tabs: @Composable (() -> Unit),
) {
  val paint = remember {
    Paint().apply {
      isAntiAlias = true
      color = android.graphics.Color.WHITE
      setShadowLayer(
        40f,
        0f,
        0f,
        Color.Black.copy(alpha = 0.1f).toArgb()
      )
    }
  }

  Row(
    modifier = modifier
      .height(60.dp)
      .width(130.dp)
      .drawBehind {
        drawIntoCanvas { canvas ->
          val rect = RectF(0f, 0f, size.width, size.height)
          canvas.nativeCanvas.drawRoundRect(
            rect,
            30.dp.toPx(),
            30.dp.toPx(),
            paint
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

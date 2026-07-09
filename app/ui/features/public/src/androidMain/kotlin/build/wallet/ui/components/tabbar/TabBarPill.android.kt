package build.wallet.ui.components.tabbar

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  selectedIndex: Int,
  tabCount: Int,
  tabs: @Composable RowScope.() -> Unit,
) {
  val theme = LocalTheme.current
  val pillWidth = 160.dp
  val pillHeight = 60.dp
  val indicatorInset = 4.dp
  val indicatorOverlap = 6.dp
  val pillBackgroundColor =
    if (theme == Theme.DARK) {
      WalletTheme.colors.subtleBackground
    } else {
      WalletTheme.colors.tabBarBackground
    }
  val indicatorColor =
    if (theme == Theme.DARK) {
      WalletTheme.colors.secondary
    } else {
      WalletTheme.colors.subtleBackground
    }
  val paint = remember {
    Paint().apply {
      isAntiAlias = true
      color = Color.Transparent.toArgb()
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
      .height(pillHeight)
      .width(pillWidth)
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
      .background(pillBackgroundColor, shape = RoundedCornerShape(30.dp)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (tabCount > 0) {
        val clampedIndex = selectedIndex.coerceIn(0, tabCount - 1)
        val baseWidth = (pillWidth - indicatorInset * 2) / tabCount
        val indicatorWidth = baseWidth + indicatorOverlap
        val indicatorHeight = pillHeight - indicatorInset * 2
        val minOffset = indicatorInset
        val maxOffset = pillWidth - indicatorInset - indicatorWidth
        val rawOffset = indicatorInset + baseWidth * clampedIndex - indicatorOverlap / 2
        val indicatorOffsetX: Dp by animateDpAsState(
          targetValue = rawOffset.coerceIn(minOffset, maxOffset),
          animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
          label = "TabBarIndicatorOffset"
        )

        Box(
          modifier = Modifier
            .offset(x = indicatorOffsetX, y = indicatorInset)
            .size(width = indicatorWidth, height = indicatorHeight)
            .background(
              color = indicatorColor,
              shape = RoundedCornerShape(30.dp)
            )
        )
      }

      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = tabs
      )
    }
  }
}

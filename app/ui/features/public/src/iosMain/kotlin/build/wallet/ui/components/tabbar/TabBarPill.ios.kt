package build.wallet.ui.components.tabbar

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

// controls the number of shadow layers
private const val SHADOW_LAYER_COUNT = 6

// controls the distance of each shadow layer from the main pill
private const val OFFSET_MULTIPLIER = 6f

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  selectedIndex: Int,
  tabCount: Int,
  tabs: @Composable RowScope.() -> Unit,
) {
  val isDesignSystemV2Enabled = true
  val theme = LocalTheme.current
  val pillWidth = if (isDesignSystemV2Enabled) 160.dp else 130.dp
  val pillHeight = 60.dp
  val indicatorInset = 4.dp
  val indicatorOverlap = 6.dp
  val pillBackgroundColor =
    if (isDesignSystemV2Enabled && theme == Theme.DARK) {
      WalletTheme.colors.subtleBackground
    } else {
      WalletTheme.colors.tabBarBackground
    }
  val indicatorColor =
    if (isDesignSystemV2Enabled) {
      if (theme == Theme.DARK) WalletTheme.colors.secondary else WalletTheme.colors.subtleBackground
    } else {
      WalletTheme.colors.foreground10
    }
  Row(
    modifier = modifier
      .height(pillHeight)
      .width(pillWidth)
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
      .background(pillBackgroundColor, shape = RoundedCornerShape(30.dp)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = if (isDesignSystemV2Enabled) Arrangement.Center else Arrangement.SpaceEvenly
  ) {
    if (isDesignSystemV2Enabled) {
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
    } else {
      tabs()
    }
  }
}

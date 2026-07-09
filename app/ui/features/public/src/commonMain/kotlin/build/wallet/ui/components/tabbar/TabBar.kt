package build.wallet.ui.components.tabbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme

@Composable
fun TabBar(
  modifier: Modifier = Modifier,
  selectedIndex: Int,
  tabCount: Int,
  tabs: @Composable RowScope.() -> Unit,
) {
  val gradientBackground = WalletTheme.colors.background
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val gradient =
    Brush.verticalGradient(
      colorStops =
        arrayOf(
          0f to Color.Transparent,
          0.4f to gradientBackground.copy(alpha = 0.60f),
          1f to gradientBackground.copy(alpha = 1.0f)
        )
    )
  Box(
    modifier = modifier.fillMaxWidth()
  ) {
    Canvas(
      modifier = Modifier.fillMaxWidth().height(75.dp + bottomInset),
      onDraw = {
        drawRect(gradient)
      }
    )
    TabBarPill(
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .padding(top = 10.dp)
          .clickable(false) {},
      selectedIndex = selectedIndex,
      tabCount = tabCount
    ) {
      tabs()
    }
  }
}

@Composable
fun Tab(
  icon: Icon,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badged: Boolean = false,
) {
  Box {
    Icon(
      icon = icon,
      size = IconSize.Small,
      color = if (selected) WalletTheme.colors.foreground else WalletTheme.colors.foreground30,
      modifier = modifier
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          onClick = onClick
        )
    )

    if (badged) {
      val badgeColor = WalletTheme.colors.warningForeground
      val badgeBorder = WalletTheme.colors.tabBarBackground
      Canvas(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
        drawCircle(
          color = badgeColor,
          radius = 6.dp.toPx()
        )

        drawCircle(
          color = badgeBorder,
          radius = 6.dp.toPx(),
          style = Stroke(width = 2.dp.toPx())
        )
      }
    }
  }
}

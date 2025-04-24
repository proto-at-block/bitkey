package build.wallet.ui.components.tabbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
  tabs: @Composable () -> Unit,
) {
  val gradientBackground = WalletTheme.colors.background
  Box(
    modifier = modifier.fillMaxWidth()
  ) {
    Canvas(
      modifier = Modifier.fillMaxWidth().height(75.dp),
      onDraw = {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, gradientBackground)))
      }
    )
    Row(
      modifier = Modifier.align(Alignment.TopCenter)
        .height(60.dp)
        .width(130.dp)
        .shadow(
          elevation = 2.dp,
          shape = RoundedCornerShape(30.dp),
          ambientColor = Color.Black.copy(.1f)
        )
        .background(
          color = WalletTheme.colors.tabBarBackground,
          shape = RoundedCornerShape(30.dp)
        )
        .clickable(false) { },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceEvenly
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
      val badgeColor = WalletTheme.colors.bitkeyPrimary
      val badgeBorder = WalletTheme.colors.tabBarBackground
      Canvas(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
        drawCircle(
          color = badgeColor,
          radius = 4.dp.toPx()
        )

        drawCircle(
          color = badgeBorder,
          radius = 4.dp.toPx(),
          style = Stroke(width = 1.5.dp.toPx())
        )
      }
    }
  }
}

package build.wallet.ui.components.tabbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        .height(56.dp)
        .width(120.dp)
        .padding(bottom = 8.dp)
        .shadow(1.dp, shape = RoundedCornerShape(28.dp))
        .background(
          color = WalletTheme.colors.tabBarBackground,
          shape = RoundedCornerShape(28.dp)
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
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // TODO W-11047 : Add icons and selection states
  val size = if (selected) 30.dp else 25.dp
  Box(
    modifier.size(size).background(Color.Red, shape = CircleShape)
      .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
  )
}

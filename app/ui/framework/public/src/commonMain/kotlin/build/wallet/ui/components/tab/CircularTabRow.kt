package build.wallet.ui.components.tab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.tab.CircularTabRowModel
import build.wallet.ui.theme.WalletTheme
import kotlinx.collections.immutable.ImmutableList

private val EaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

/*
 A Circular Tab Row is an outlined component where each end is a half circle. The active tab
 is highlighted with a filled indicator (CircularTabIndicator), with the ends also using the half-circle design.
 */
@Composable
fun CircularTabRow(
  modifier: Modifier = Modifier,
  model: CircularTabRowModel,
) {
  CircularTabRow(
    modifier = modifier,
    items = model.items,
    selectedItemIndex = model.selectedItemIndex,
    onClick = model.onClick
  )
}

@Composable
fun CircularTabRow(
  items: ImmutableList<String>,
  selectedItemIndex: Int,
  modifier: Modifier = Modifier,
  onClick: (Int) -> Unit,
  backgroundColor: Color = WalletTheme.colors.subtleBackground,
) {
  BoxWithConstraints(
    modifier = modifier
      .wrapContentHeight()
      .fillMaxWidth()
      .clip(shape = CircleShape)
      .background(backgroundColor)
      .padding(all = 4.dp)
  ) {
    val tabWidth = remember(maxWidth, items.size) { maxWidth / items.size }
    val indicatorOffset by animateDpAsState(
      targetValue = tabWidth * selectedItemIndex,
      animationSpec = tween(easing = EaseInOut, durationMillis = 200)
    )

    Box(modifier = Modifier.matchParentSize()) {
      CircularTabIndicator(
        modifier = Modifier
          .fillMaxHeight()
          .requiredWidth(tabWidth)
          .offset(indicatorOffset)
      )
    }
    Row(
      horizontalArrangement = Arrangement.Center
    ) {
      items.forEachIndexed { index, text ->
        CircularTabItem(
          modifier = Modifier.weight(1f),
          isSelected = index == selectedItemIndex,
          onClick = { onClick(index) },
          text = text
        )
      }
    }
  }
}

@Composable
private fun CircularTabIndicator(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .shadow(1.dp, CircleShape)
      .clip(shape = CircleShape)
      .background(color = WalletTheme.colors.background)
  )
}

@Composable
private fun CircularTabItem(
  isSelected: Boolean,
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
) {
  val colors = WalletTheme.colors
  val targetTextColor = remember(isSelected, colors) {
    if (isSelected) colors.foreground else colors.foreground60
  }
  val tabTextColor: Color by animateColorAsState(
    targetValue = targetTextColor,
    animationSpec = tween(easing = EaseInOut)
  )
  val tabTextWeight = remember(isSelected) {
    if (isSelected) FontWeight.Bold else FontWeight.Normal
  }

  Text(
    modifier = modifier
      .clickable(
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      )
      .padding(vertical = 8.dp, horizontal = 12.dp),
    text = text,
    color = tabTextColor,
    fontWeight = tabTextWeight,
    textAlign = TextAlign.Center
  )
}

package build.wallet.ui.components.tab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.tab.CircularTabRowBodyModel
import build.wallet.ui.theme.WalletTheme

private val EaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

@Composable
internal fun CircularTabRow(model: CircularTabRowBodyModel) {
  BoxWithConstraints(
    modifier = Modifier
      .border(
        width = 2.dp,
        color = WalletTheme.colors.foreground10,
        shape = CircleShape
      ).padding(all = 7.dp)
      .clip(shape = CircleShape)
      .background(color = Color.White)
  ) {
    val tabWidth = remember(this.maxWidth, model.items.size) { this.maxWidth / model.items.size }
    val indicatorOffset: Dp by animateDpAsState(
      targetValue = tabWidth * model.selectedItemIndex,
      animationSpec = tween(easing = EaseInOut)
    )

    CircularTabIndicator(
      indicatorWidth = tabWidth,
      indicatorHeight = this.maxHeight,
      indicatorOffset = indicatorOffset,
      indicatorColor = WalletTheme.colors.foreground10
    )
    Row(
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.clip(shape = CircleShape)
    ) {
      model.items.forEachIndexed { index, text ->
        CircularTabItem(
          isSelected = index == model.selectedItemIndex,
          onClick = {
            model.onClick(index)
          },
          tabWidth = tabWidth,
          text = text,
          textWeight = FontWeight.Normal,
          textColor = WalletTheme.colors.foreground60,
          selectedTextColor = WalletTheme.colors.foreground,
          selectedTextWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun CircularTabIndicator(
  indicatorWidth: Dp,
  indicatorHeight: Dp,
  indicatorOffset: Dp,
  indicatorColor: Color,
) {
  Box(
    modifier = Modifier
      .width(width = indicatorWidth)
      .height(height = indicatorHeight)
      .offset(x = indicatorOffset)
      .clip(shape = CircleShape)
      .background(color = indicatorColor)
  )
}

@Composable
private fun CircularTabItem(
  isSelected: Boolean,
  onClick: () -> Unit,
  tabWidth: Dp,
  text: String,
  selectedTextColor: Color,
  selectedTextWeight: FontWeight,
  textColor: Color,
  textWeight: FontWeight,
) {
  val targetTextColor = remember(isSelected) {
    if (isSelected) selectedTextColor else textColor
  }
  val tabTextColor: Color by animateColorAsState(
    targetValue = targetTextColor,
    animationSpec = tween(easing = EaseInOut)
  )
  val tabTextWeight = remember(isSelected) {
    if (isSelected) selectedTextWeight else textWeight
  }

  Text(
    modifier = Modifier
      .clip(shape = CircleShape)
      .clickable(
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ).width(width = tabWidth)
      .padding(vertical = 8.dp, horizontal = 12.dp),
    text = text,
    color = tabTextColor,
    fontWeight = tabTextWeight,
    textAlign = TextAlign.Center
  )
}
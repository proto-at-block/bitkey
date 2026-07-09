package build.wallet.statemachine.money.currency

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter

data class MoneyHomeHeroModel(
  val primaryAmount: String,
  val secondaryAmount: String,
  val isHidden: Boolean,
  val isPriceGraphEnabled: Boolean,
  val selectedSection: AppearanceSection,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    val easeOutCubic = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)
    val isDarkMode = LocalTheme.current == Theme.DARK

    val image = when {
      isDarkMode && isPriceGraphEnabled -> Icon.MoneyHomeHeroDarkWithGraph.painter()
      isDarkMode && !isPriceGraphEnabled -> Icon.MoneyHomeHeroDarkNoGraph.painter()
      !isDarkMode && isPriceGraphEnabled -> Icon.MoneyHomeHeroLightWithGraph.painter()
      !isDarkMode && !isPriceGraphEnabled -> Icon.MoneyHomeHeroLightNoGraph.painter()
      else -> error("Invalid combination of isDarkMode and isPriceGraphEnabled")
    }
    val scale by animateFloatAsState(
      targetValue = when (selectedSection) {
        AppearanceSection.DISPLAY -> .9f
        AppearanceSection.CURRENCY -> 1.2f
        AppearanceSection.PRIVACY -> 2.0f
      },
      animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
      label = "scale"
    )

    val scaleBalance by animateFloatAsState(
      targetValue = when (selectedSection) {
        AppearanceSection.DISPLAY -> .4f
        AppearanceSection.CURRENCY -> .6f
        AppearanceSection.PRIVACY -> 1.1f
      },
      animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
      label = "scaleBalance"
    )

    val balanceOffsetY by animateDpAsState(
      targetValue = when (selectedSection) {
        AppearanceSection.DISPLAY -> (-38).dp
        AppearanceSection.CURRENCY -> 4.dp
        AppearanceSection.PRIVACY -> 35.dp
      },
      animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
      label = "balanceOffsetY"
    )

    val offsetY by animateDpAsState(
      targetValue = when (selectedSection) {
        AppearanceSection.DISPLAY -> 0.dp
        AppearanceSection.CURRENCY -> 60.dp
        AppearanceSection.PRIVACY -> 140.dp
      },
      animationSpec = tween(durationMillis = 300, easing = easeOutCubic),
      label = "offsetY"
    )

    Box {
      Image(
        painter = image,
        contentDescription = "money home hero",
        alignment = Alignment.TopCenter,
        modifier = Modifier
          .then(modifier)
          .align(Alignment.Center)
          .clipToBounds()
          .background(
            color = WalletTheme.colors.subtleBackground,
            shape = RoundedCornerShape(12.dp)
          )
          .offset(y = offsetY)
          .fillMaxWidth()
          .height(200.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
      )

      CollapsibleLabelContainer(
        modifier = Modifier
          .padding(vertical = 64.dp)
          .align(Alignment.TopCenter)
          .offset(y = balanceOffsetY)
          .graphicsLayer {
            scaleX = scaleBalance
            scaleY = scaleBalance
          },
        collapsed = isHidden,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        topContent = { Label(primaryAmount, type = LabelType.Body2Bold) },
        bottomContent = {
          Label(
            secondaryAmount,
            type = LabelType.Body4Medium,
            treatment = LabelTreatment.Secondary
          )
        },
        collapsedContent = { placeholder ->
          CollapsedMoneyView(
            height = 16.dp,
            shimmer = !placeholder
          )
        }
      )
    }
  }
}

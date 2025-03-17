package build.wallet.ui.components.banner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

val BannerHeight = 44.dp

@Composable
fun Banner(
  modifier: Modifier = Modifier,
  text: String,
  leadingIcon: Icon? = null,
  treatment: BannerTreatment,
  size: BannerSize,
  onClick: () -> Unit = { },
) {
  val style = WalletTheme.bannerStyle(treatment = treatment)
  val widthModifier =
    when (size) {
      BannerSize.Compact -> Modifier
      BannerSize.Large -> Modifier.fillMaxWidth()
    }
  val contentColor by animateColorAsState(style.contentColor)
  val backgroundColor by animateColorAsState(style.backgroundColor)
  Box(
    modifier =
      modifier
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          onClick = onClick
        )
        .then(widthModifier)
        .height(BannerHeight)
        .background(color = backgroundColor, shape = RoundedCornerShape(24.dp))
        .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      leadingIcon?.let {
        Icon(
          icon = leadingIcon,
          size = IconSize.Small,
          color = contentColor
        )
      }
      Label(
        text = text,
        style =
          WalletTheme.labelStyle(
            type = LabelType.Body3Medium,
            treatment = LabelTreatment.Unspecified,
            textColor = contentColor
          )
      )
    }
  }
}

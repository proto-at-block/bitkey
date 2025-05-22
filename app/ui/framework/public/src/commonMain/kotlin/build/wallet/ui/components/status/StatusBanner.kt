package build.wallet.ui.components.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.IconStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

// All status banners currently use a warning background color
@Composable
fun StatusBannerModel.backgroundColor(): Color {
  val theme = LocalTheme.current
  return when (style) {
    // when the theme is light, use a lighter color for the destructive banner
    // otherwise we always use the warning color
    BannerStyle.Destructive -> if (theme == Theme.LIGHT) {
      WalletTheme.colors.destructiveForeground.copy(.1f)
    } else {
      WalletTheme.colors.warning
    }
    BannerStyle.Warning -> WalletTheme.colors.warning
  }
}

@Composable
fun StatusBanner(
  modifier: Modifier = Modifier,
  model: StatusBannerModel,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(model.backgroundColor())
      .statusBarsPadding()
      .thenIf(model.onClick != null) {
        model.onClick?.let {
          Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = it
          )
        } ?: Modifier
      }
      .padding(horizontal = 20.dp)
      .padding(top = 12.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusBannerLabel(text = model.title, type = LabelType.Body3Medium, style = model.style)
      model.onClick?.let {
        IconImage(
          modifier = Modifier.padding(start = 4.dp),
          model =
            IconModel(
              iconImage = IconImage.LocalImage(Icon.SmallIconInformationFilled),
              iconSize = IconSize.XSmall
            ),
          style =
            IconStyle(
              color = when (model.style) {
                BannerStyle.Destructive -> WalletTheme.colors.destructiveForeground
                BannerStyle.Warning -> WalletTheme.colors.warningForeground
              }
            )
        )
      }
    }

    model.subtitle?.let {
      StatusBannerLabel(text = it, type = LabelType.Body4Regular, style = model.style)
    }
  }
}

@Composable
private fun StatusBannerLabel(
  text: String,
  type: LabelType,
  style: BannerStyle,
) {
  Label(
    text = text,
    type = type,
    treatment = LabelTreatment.Unspecified,
    color = when (style) {
      BannerStyle.Destructive -> WalletTheme.colors.destructiveForeground
      BannerStyle.Warning -> WalletTheme.colors.warningForeground
    },
    alignment = TextAlign.Center
  )
}

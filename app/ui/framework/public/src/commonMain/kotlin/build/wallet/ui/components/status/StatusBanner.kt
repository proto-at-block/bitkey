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
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.StyleDictionaryColors
import build.wallet.ui.tokens.darkStyleDictionaryColors
import build.wallet.ui.tokens.lightStyleDictionaryColorsDesignSystemUpdates

internal data class StatusBannerColors(
  val backgroundColor: Color,
  val contentColor: Color,
)

internal fun statusBannerColors(
  style: BannerStyle,
  theme: Theme,
): StatusBannerColors {
  val colors = when (theme) {
    Theme.LIGHT -> lightStyleDictionaryColorsDesignSystemUpdates
    Theme.DARK -> darkStyleDictionaryColors
  }

  return statusBannerColors(
    style = style,
    theme = theme,
    colors = colors
  )
}

private fun statusBannerColors(
  style: BannerStyle,
  theme: Theme,
  colors: StyleDictionaryColors,
): StatusBannerColors {
  val useDarkPaletteOnLight = theme == Theme.LIGHT
  val contentColors = if (useDarkPaletteOnLight) darkStyleDictionaryColors else colors

  val backgroundColor = if (useDarkPaletteOnLight) {
    colors.inverseBackground
  } else {
    when (style) {
      BannerStyle.Destructive -> if (theme == Theme.LIGHT) {
        colors.destructiveForeground.copy(alpha = 0.1f)
      } else {
        colors.warning
      }

      BannerStyle.Warning -> colors.warning
    }
  }

  val contentColor = when (style) {
    BannerStyle.Destructive -> contentColors.destructiveForeground
    BannerStyle.Warning -> contentColors.warningForeground
  }

  return StatusBannerColors(
    backgroundColor = backgroundColor,
    contentColor = contentColor
  )
}

@Composable
fun StatusBannerModel.backgroundColor(): Color {
  return colors().backgroundColor
}

@Composable
private fun StatusBannerModel.colors(): StatusBannerColors {
  return statusBannerColors(
    style = style,
    theme = LocalTheme.current
  )
}

@Composable
fun StatusBanner(
  modifier: Modifier = Modifier,
  model: StatusBannerModel,
) {
  val bannerColors = model.colors()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(bannerColors.backgroundColor)
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
      StatusBannerLabel(
        text = model.title,
        type = LabelType.Body3Medium,
        contentColor = bannerColors.contentColor
      )
      model.onClick?.let {
        IconImage(
          modifier = Modifier.padding(start = 4.dp),
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.Information),
            iconSize = IconSize.XSmall
          ),
          color = bannerColors.contentColor
        )
      }
    }

    model.subtitle?.let {
      StatusBannerLabel(
        text = it,
        type = LabelType.Body4Regular,
        contentColor = bannerColors.contentColor
      )
    }
  }
}

@Composable
private fun StatusBannerLabel(
  text: String,
  type: LabelType,
  contentColor: Color,
) {
  Label(
    text = text,
    type = type,
    treatment = LabelTreatment.Unspecified,
    color = contentColor,
    alignment = TextAlign.Center
  )
}

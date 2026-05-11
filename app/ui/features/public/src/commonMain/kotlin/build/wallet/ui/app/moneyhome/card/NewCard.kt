package build.wallet.ui.app.moneyhome.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconArrowRight
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A composable for rendering cards in the new UI style.
 *
 * Note: this should be aligned with the design system and old cards should be deprecated in the
 * future.
 */
@Composable
fun NewCard(
  modifier: Modifier = Modifier,
  model: CardModel,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val theme = LocalTheme.current
  val cornerRadius = if (isDesignSystemV2Enabled) 8.dp else 16.dp
  val isInverseBackgroundDsv2Card =
    isDesignSystemV2Enabled &&
      model.style is CardModel.CardStyle.Gradient &&
      model.style.backgroundColor == CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground

  Card(
    modifier = modifier.scalingClickable(enabled = model.onClick != null) {
      model.onClick?.invoke()
    }.shadow(
      elevation = 2.dp,
      shape = RoundedCornerShape(cornerRadius),
      ambientColor = Color.Black.copy(.1f)
    ),
    backgroundColor = when {
      isInverseBackgroundDsv2Card && theme == Theme.DARK -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.containerBackground
    },
    cornerRadius = cornerRadius,
    paddingValues = PaddingValues(vertical = 16.dp, horizontal = 14.dp),
    borderWidth = 0.dp
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      model.leadingImage?.let {
        CardImage(
          model = it,
          style = model.style
        )
        Spacer(modifier = Modifier.width(12.dp))
      }
      Column(
        modifier = Modifier.weight(1F),
        verticalArrangement = Arrangement.SpaceAround
      ) {
        model.title?.let { title ->
          Label(
            model = title,
            type = LabelType.Body3Medium
          )
        }

        model.subtitle?.let {
          if (model.style is CardModel.CardStyle.Outline) {
            Spacer(modifier = Modifier.height(8.dp))
          }

          Label(
            text = it,
            style = WalletTheme.labelStyle(
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary
            )
          )
        }
      }
      Spacer(modifier = Modifier.width(20.dp))
      IconButton(
        iconModel = IconModel(
          icon = SmallIconArrowRight,
          iconSize = Accessory,
          iconBackgroundType = Transient,
          iconTint = IconTint.On30
        ),
        onClick = {
          model.onClick?.invoke()
        }
      )
      Spacer(modifier = Modifier.width(2.dp))
    }
  }
}

@Composable
private fun CardImage(
  model: CardModel.CardImage,
  style: CardModel.CardStyle,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val theme = LocalTheme.current

  when (model) {
    is CardModel.CardImage.StaticImage -> {
      val icon =
        when (model.icon) {
          Icon.MediumIconTrustedContact -> Icon.SmallIconShieldPerson
          else -> model.icon
        }
      val isInverseBackgroundDsv2Card =
        isDesignSystemV2Enabled &&
          style is CardModel.CardStyle.Gradient &&
          style.backgroundColor == CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground

      IconImage(
        iconImage = LocalImage(icon),
        size = Small,
        color = when {
          isInverseBackgroundDsv2Card && theme == Theme.DARK -> WalletTheme.colors.subtleBackground
          else -> Color.Unspecified
        },
        tint = when {
          isInverseBackgroundDsv2Card && theme == Theme.DARK -> null
          else -> IconTint.White
        },
        background = IconBackgroundType.Circle(
          color = when {
            isInverseBackgroundDsv2Card -> IconBackgroundType.Circle.CircleColor.InverseBackground
            else -> IconBackgroundType.Circle.CircleColor.BitkeyPrimary
          },
          circleSize = IconSize.Large
        )
      )
    }

    is CardModel.CardImage.DynamicImage.HardwareReplacementStatusProgress ->
      Box(
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          size = 40.dp,
          progress = model.progress.value,
          direction = CounterClockwise,
          remainingSeconds = model.remainingSeconds,
          indicatorColor = WalletTheme.colors.yourBalancePrimary,
          backgroundColor = WalletTheme.colors.yourBalancePrimary.copy(alpha = .1f),
          strokeWidth = 5.dp
        )
        Icon(
          icon = SmallIconBitkey,
          size = Small,
          color = WalletTheme.colors.yourBalancePrimary
        )
      }
  }
}

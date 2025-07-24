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
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
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
  Card(
    modifier = modifier.scalingClickable(enabled = model.onClick != null) {
      model.onClick?.invoke()
    }.shadow(
      elevation = 2.dp,
      shape = RoundedCornerShape(16.dp),
      ambientColor = Color.Black.copy(.1f)
    ),
    paddingValues = PaddingValues(vertical = 16.dp, horizontal = 14.dp),
    borderWidth = 0.dp
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      model.leadingImage?.let {
        CardImage(it)
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
private fun CardImage(model: CardModel.CardImage) {
  when (model) {
    is CardModel.CardImage.StaticImage ->
      IconImage(
        model = IconModel(
          // This is a workaround for the TrustedContact icon to support both cards
          // TODO: Remove this workaround when the Security Hub FF flag is removed
          icon = when (model.icon) {
            Icon.MediumIconTrustedContact -> Icon.SmallIconShieldPerson
            else -> model.icon
          },
          iconSize = Small,
          iconTint = IconTint.White,
          iconBackgroundType = IconBackgroundType.Circle(
            color = IconBackgroundType.Circle.CircleColor.BitkeyPrimary,
            circleSize = IconSize.Large
          )
        )
      )

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

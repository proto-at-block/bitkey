package build.wallet.ui.app.moneyhome.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon.ArrowRight
import build.wallet.statemachine.core.Icon.Bitkey
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.components.card.CardContainer
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * Renders a [CardModel.Status] card. Used both inside money home (via
 * [StatusCardContainer]) and directly by callers like Security Hub.
 */
@Composable
fun StatusCard(
  modifier: Modifier = Modifier,
  model: CardModel.Status,
) {
  val theme = LocalTheme.current
  val cornerRadius = 8.dp
  val isInverseBackground = model.inverse

  CardContainer(
    modifier = modifier.shadow(
      elevation = 2.dp,
      shape = RoundedCornerShape(cornerRadius),
      ambientColor = Color.Black.copy(.1f)
    ),
    backgroundColor = when {
      isInverseBackground && theme == Theme.DARK -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.containerBackground
    },
    cornerRadius = cornerRadius,
    paddingValues = PaddingValues(vertical = 16.dp, horizontal = 14.dp),
    borderWidth = 0.dp
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      CardImage(
        model = model.leadingImage,
        isInverseBackground = isInverseBackground
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(
        modifier = Modifier.weight(1F),
        verticalArrangement = Arrangement.SpaceAround
      ) {
        model.title?.let {
          Label(
            text = it,
            type = LabelType.Body3Medium
          )
        }

        model.subtitle?.let {
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
      Icon(
        icon = ArrowRight,
        size = Accessory,
        color = WalletTheme.colors.foreground30
      )
      Spacer(modifier = Modifier.width(2.dp))
    }
  }
}

@Composable
private fun CardImage(
  model: CardModel.Status.Image,
  isInverseBackground: Boolean,
) {
  val theme = LocalTheme.current

  when (model) {
    is CardModel.Status.Image.StaticImage -> {
      IconImage(
        model = IconModel(
          iconImage = LocalImage(model.icon),
          iconSize = Small,
          iconTint = when {
            isInverseBackground && theme == Theme.DARK -> null
            else -> IconTint.White
          },
          iconBackgroundType = IconBackgroundType.Circle(
            color = when {
              isInverseBackground -> IconBackgroundType.Circle.CircleColor.InverseBackground
              else -> IconBackgroundType.Circle.CircleColor.BitkeyPrimary
            },
            circleSize = IconSize.Large
          )
        ),
        color = when {
          isInverseBackground && theme == Theme.DARK -> WalletTheme.colors.subtleBackground
          else -> Color.Unspecified
        }
      )
    }

    is CardModel.Status.Image.HardwareReplacementStatusProgress ->
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
          icon = Bitkey,
          size = Small,
          color = WalletTheme.colors.yourBalancePrimary
        )
      }
  }
}

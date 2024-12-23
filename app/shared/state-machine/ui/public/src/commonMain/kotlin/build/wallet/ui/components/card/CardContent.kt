package build.wallet.ui.components.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon.BitkeyDeviceRaisedSmall
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter

@Composable
fun CardContent(
  modifier: Modifier = Modifier,
  model: CardModel,
) {
  Column {
    if (model.style is CardModel.CardStyle.Callout) {
      Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Callout(model = model.style.model)
      }
    } else {
      // Hero image
      model.heroImage?.let {
        Image(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
          contentScale = ContentScale.FillWidth,
          painter = it.painter(),
          contentDescription = ""
        )
      }

      // Title + Content
      Column(modifier = modifier) {
        // Title + Subtitle + Leading Image
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          model.leadingImage?.let {
            CardImage(it)
            Spacer(modifier = Modifier.width(12.dp))
          }
          Column(verticalArrangement = Arrangement.SpaceAround) {
            model.title?.let { title ->
              Label(
                model = title,
                type = LabelType.Title2
              )
            }

            model.subtitle?.let {
              Label(
                text = it,
                style =
                  WalletTheme.labelStyle(
                    type = LabelType.Body3Regular,
                    treatment = LabelTreatment.Secondary
                  )
              )
            }
          }
          model.trailingButton?.let { trailingButton ->
            Spacer(modifier = Modifier.weight(1F))
            Spacer(modifier = Modifier.width(20.dp))
            Button(
              model = trailingButton
            )
          }
        }

        // Content
        when (val content = model.content) {
          is DrillList ->
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
              DrillListContent(model = content)
            }
          is BitcoinPrice -> {
            BitcoinPriceContent(model = content)
          }
          is CardModel.CardContent.PendingClaim -> {
            PendingClaimContent(model = content)
          }
          null -> {}
        }
      }
    }
  }
}

@Composable
private fun CardImage(model: CardModel.CardImage) {
  when (model) {
    is CardModel.CardImage.StaticImage ->
      Icon(
        icon = model.icon,
        size = Small,
        color = when (model.iconTint) {
          null -> Color.Unspecified
          CardModel.CardImage.StaticImage.IconTint.Warning -> WalletTheme.colors.warningForeground
        }
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
          indicatorColor = WalletTheme.colors.containerHighlightForeground,
          strokeWidth = 3.dp
        )
        Icon(icon = BitkeyDeviceRaisedSmall, size = Small)
      }
  }
}

@Composable
private fun DrillListContent(model: DrillList) {
  model.items.forEachIndexed { index, rowModel ->
    ListItem(model = rowModel)
    if (index < model.items.lastIndex) {
      Divider()
    }
  }
}

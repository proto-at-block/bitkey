package build.wallet.ui.components.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.components.button.OrderedButtonPair
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter
import kotlinx.collections.immutable.ImmutableList

/**
 * Renders the body of a [CardModel.Hero] card: hero image, title, optional subtitle, and optional
 * primary/secondary CTAs.
 */
// Modifier intentionally targets the title+content section, excluding the header image.
@Suppress("ModifierNotUsedAtRoot")
@Composable
fun HeroCardContent(
  modifier: Modifier = Modifier,
  model: CardModel.Hero,
) {
  Column {
    Image(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
      contentScale = ContentScale.FillWidth,
      painter = model.heroImage.painter(),
      contentDescription = ""
    )

    Column(modifier = modifier) {
      Label(
        model = model.title,
        type = LabelType.Title2,
        treatment = LabelTreatment.Primary
      )

      model.subtitle?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Label(
          text = it,
          style =
            WalletTheme.labelStyle(
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary
            )
        )
      }

      if (model.primaryButton != null || model.secondaryButton != null) {
        Spacer(modifier = Modifier.height(16.dp))
        OrderedButtonPair(
          primary = model.primaryButton,
          secondary = model.secondaryButton,
          spacing = 16.dp
        )
      }
    }
  }
}

/**
 * Renders the body of a [CardModel.DrillList] card: title followed by a list of drill-down rows.
 */
@Composable
fun DrillListCardContent(
  modifier: Modifier = Modifier,
  model: CardModel.DrillList,
) {
  Column(modifier = modifier) {
    Label(
      model = model.title,
      type = LabelType.Title2,
      treatment = LabelTreatment.Primary
    )
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
      DrillListItems(items = model.items)
    }
  }
}

/**
 * Renders the body of a [CardModel.BitcoinPrice] card.
 */
@Composable
fun BitcoinPriceCardContent(
  modifier: Modifier = Modifier,
  model: CardModel.BitcoinPrice,
) {
  Column(modifier = modifier) {
    BitcoinPriceContent(model = model)
  }
}

@Composable
private fun DrillListItems(items: ImmutableList<ListItemModel>) {
  items.forEachIndexed { index, rowModel ->
    ListItem(model = rowModel)
    if (index < items.lastIndex) {
      Divider()
    }
  }
}

package build.wallet.ui.app.moneyhome.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.cardOnClick
import build.wallet.ui.components.card.BitcoinPriceCardContent
import build.wallet.ui.components.card.CardContainer
import build.wallet.ui.components.card.DrillListCardContent
import build.wallet.ui.components.card.HeroCardContent
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

@Composable
fun Card(
  modifier: Modifier = Modifier,
  model: CardModel,
) {
  val onClick = model.cardOnClick
  val cardModifier = modifier.scalingClickable(enabled = onClick != null) {
    onClick?.invoke()
  }
  model.render(cardModifier)
}

@Composable
fun HeroCardContainer(
  modifier: Modifier = Modifier,
  model: CardModel.Hero,
) {
  val backgroundColor = when (model.surfaceTreatment) {
    CardModel.SurfaceTreatment.ContainerBackground -> WalletTheme.colors.containerBackground
    CardModel.SurfaceTreatment.Background -> WalletTheme.colors.background
  }
  CardContainer(
    modifier = modifier,
    backgroundColor = backgroundColor,
    cornerRadius = 16.dp,
    borderWidth = 1.dp,
    paddingValues = PaddingValues(0.dp)
  ) {
    HeroCardContent(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
      model = model
    )
  }
}

@Composable
fun DrillListCardContainer(
  modifier: Modifier = Modifier,
  model: CardModel.DrillList,
) {
  CardContainer(
    modifier = modifier,
    backgroundColor = WalletTheme.colors.containerBackground,
    cornerRadius = 16.dp,
    borderWidth = 1.dp,
    paddingValues = PaddingValues(0.dp)
  ) {
    DrillListCardContent(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 0.dp),
      model = model
    )
  }
}

@Composable
fun BitcoinPriceCardContainer(
  modifier: Modifier = Modifier,
  model: CardModel.BitcoinPrice,
) {
  val isDarkTheme = LocalTheme.current == Theme.DARK
  CardContainer(
    modifier = modifier,
    backgroundColor = WalletTheme.colors.containerBackground,
    cornerRadius = 8.dp,
    borderWidth = if (!isDarkTheme) 0.dp else 1.dp,
    paddingValues = PaddingValues(0.dp)
  ) {
    BitcoinPriceCardContent(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          top = 12.dp,
          start = 16.dp,
          end = 16.dp,
          bottom = 14.dp
        ),
      model = model
    )
  }
}

@Composable
fun StatusCardContainer(
  modifier: Modifier = Modifier,
  model: CardModel.Status,
) {
  StatusCard(modifier = modifier, model = model)
}



package build.wallet.ui.app.moneyhome.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Height
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Scale
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.card.CardContent
import build.wallet.ui.components.card.GradientCard
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.list.ListHeader
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.DurationUnit.SECONDS

private const val GETTING_STARTED_TILE_ASPECT_RATIO = 168f / 116f
private const val GETTING_STARTED_TILE_CHEVRON_SIZE_DP = 16
private val GETTING_STARTED_TILE_PEEK_WIDTH = 36.dp

@Composable
fun MoneyHomeCard(
  modifier: Modifier = Modifier,
  model: CardModel,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val isDarkTheme = LocalTheme.current == Theme.DARK

  AnimatedMoneyHomeCardContainer(
    modifier = modifier,
    model = model
  ) { cardModifier ->
    val cardKind = model.kind
    if (isDesignSystemV2Enabled && cardKind is CardModel.Kind.GettingStarted) {
      GettingStartedTilesCard(
        modifier = cardModifier,
        title = model.title?.string.orEmpty(),
        tiles = cardKind.tiles
      )
    } else {
      when (val style = model.style) {
        CardModel.CardStyle.Plain ->
          Card(
            modifier = cardModifier,
            backgroundColor = Color.Unspecified,
            paddingValues = PaddingValues(0.dp),
            borderWidth = 0.dp
          ) {
            CardContent(
              modifier = Modifier
                .fillMaxWidth(),
              model = model
            )
          }

        Outline -> {
          val isDesignSystemV2BitcoinPriceCard = isDesignSystemV2Enabled && model.content is BitcoinPrice

          Card(
            modifier = cardModifier,
            cornerRadius = if (isDesignSystemV2BitcoinPriceCard) 8.dp else 16.dp,
            borderWidth = if (isDesignSystemV2BitcoinPriceCard && !isDarkTheme) 0.dp else 1.dp,
            paddingValues = PaddingValues(0.dp)
          ) {
            val topPadding = if (isDesignSystemV2BitcoinPriceCard) 12.dp else 20.dp
            val horizontalPadding = if (isDesignSystemV2BitcoinPriceCard) 16.dp else 20.dp
            val bottomPadding = when {
              isDesignSystemV2BitcoinPriceCard -> 14.dp
              model.content == null -> 20.dp
              else -> 0.dp
            }

            CardContent(
              modifier = Modifier
                .fillMaxWidth()
                .padding(
                  top = topPadding,
                  start = horizontalPadding,
                  end = horizontalPadding,
                  bottom = bottomPadding
                ),
              model = model
            )
          }
        }

        is Gradient ->
          GradientCard(
            modifier = cardModifier,
            backgroundColor = when (style.backgroundColor) {
              Gradient.BackgroundColor.Default -> WalletTheme.colors.calloutInformationBackground
              else -> WalletTheme.colors.containerBackgroundHighlight
            }
          ) {
            CardContent(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
              model = model
            )
          }

        is CardModel.CardStyle.Callout ->
          CardContent(
            modifier = cardModifier,
            model = model
          )
      }
    }
  }
}

@Composable
private fun AnimatedMoneyHomeCardContainer(
  modifier: Modifier = Modifier,
  model: CardModel,
  content: @Composable (Modifier) -> Unit,
) {
  val localDensity = LocalDensity.current
  val height = remember { Animatable(0f) }
  val scale = remember { Animatable(1f) }
  var measuredHeightDp by remember {
    mutableStateOf<Float?>(null)
  }

  LaunchedEffect(model.animation) {
    model.animation?.let { animations ->
      height.snapTo(measuredHeightDp ?: model.estimatedHeight())
      animations.forEach { animationSet ->
        // Give each animation set a scope so that they happen one after the other and
        // use `launch` within an animation set to ensure they happen at the same time.
        coroutineScope {
          animationSet.animations.forEach {
            val animationSpec: TweenSpec<Float> =
              tween(
                durationMillis =
                  Duration.convert(
                    animationSet.durationInSeconds,
                    SECONDS,
                    MILLISECONDS
                  ).toInt()
              )
            when (val animation = it) {
              is Height ->
                launch {
                  height.animateTo(animation.value, animationSpec)
                }
              is Scale ->
                launch {
                  scale.animateTo(animation.value, animationSpec)
                }
            }
          }
        }
      }
    }
  }

  var cardModifier =
    modifier
      .thenIf(model.animation == null) {
        Modifier.onGloballyPositioned { layoutCoordinates ->
          measuredHeightDp =
            with(localDensity) {
              layoutCoordinates.size.height.toDp().value
            }
        }
      }
      .scalingClickable(enabled = model.onClick != null) {
        model.onClick?.invoke()
      }

  if (model.animation != null) {
    cardModifier = cardModifier.height(height.value.dp)
  }

  content(cardModifier.scale(scale.value))
}

@Composable
private fun GettingStartedTilesCard(
  modifier: Modifier = Modifier,
  title: String,
  tiles: ImmutableList<CardModel.GettingStartedTileModel>,
) {
  val sortedTiles = remember(tiles) {
    (
      tiles.filterNot(CardModel.GettingStartedTileModel::isComplete) +
        tiles.filter(CardModel.GettingStartedTileModel::isComplete)
    ).toImmutableList()
  }

  Column(modifier = modifier.fillMaxWidth()) {
    ListHeader(
      title = title,
      titleType = LabelType.Body3Mono
    )

    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth()
    ) {
      val tileSpacing = 10.dp
      val tileWidth = if (sortedTiles.size > 2) {
        (maxWidth - tileSpacing - GETTING_STARTED_TILE_PEEK_WIDTH) / 2
      } else {
        (maxWidth - tileSpacing) / 2
      }

      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tileSpacing)
      ) {
        items(
          items = sortedTiles,
          key = CardModel.GettingStartedTileModel::id
        ) { tile ->
          GettingStartedTile(
            task = tile,
            tileWidth = tileWidth
          )
        }
      }
    }
  }
}

@Composable
private fun GettingStartedTile(
  task: CardModel.GettingStartedTileModel,
  tileWidth: androidx.compose.ui.unit.Dp,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isClickable = task.onClick != null
  val cornerRadius = if (LocalDesignSystemUpdatesEnabled.current) 8.dp else 16.dp

  Box(
    modifier = Modifier
      .width(tileWidth)
      .aspectRatio(GETTING_STARTED_TILE_ASPECT_RATIO)
      .thenIf(isClickable) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = {
            task.onClick?.invoke()
          }
        )
      }
      .background(
        color = WalletTheme.colors.secondary,
        shape = RoundedCornerShape(cornerRadius)
      )
      .thenIf(task.isComplete) { Modifier.alpha(0.3f) }
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      task.leadingIcon?.let {
        IconImage(model = it)
      }

      if (!task.isComplete) {
        IconImage(
          modifier = Modifier.thenIf(!task.isEnabled) { Modifier.alpha(0.3f) },
          model = IconModel(
            icon = build.wallet.statemachine.core.Icon.SmallIconCaretRight,
            iconSize = IconSize.Custom(GETTING_STARTED_TILE_CHEVRON_SIZE_DP),
            iconTint = IconTint.On30
          )
        )
      }
    }

    Label(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(12.dp),
      text = task.title,
      type = LabelType.Body2Regular,
      treatment =
        when {
          task.isComplete -> LabelTreatment.Secondary
          task.isEnabled -> LabelTreatment.Primary
          else -> LabelTreatment.Disabled
        }
    )
  }
}

private fun CardModel.estimatedHeight() =
  listOfNotNull(
    20f, // top padding
    17f, // title height
    subtitle?.let { 15f }, // subtitle height
    content?.let {
      when (it) {
        is DrillList ->
          // each row height + spacing in between rows
          (it.items.count() * 56f) + ((it.items.count() - 1) * 12f)
        is BitcoinPrice -> 100f
        is CardModel.CardContent.PendingClaim -> 104f
      }
    },
    if (content == null) {
      20f
    } else {
      null
    } // bottom padding if no content
  ).sum()

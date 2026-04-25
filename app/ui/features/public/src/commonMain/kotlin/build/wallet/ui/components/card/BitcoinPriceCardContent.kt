package build.wallet.ui.components.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.*
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ui.PriceChart
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.AnimatedAmount
import build.wallet.ui.components.label.AnimatedAmountAutoResizedLabel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Subtract
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun BitcoinPriceContent(model: BitcoinPrice) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  Column(
    modifier = Modifier
      .padding(bottom = if (isDesignSystemV2Enabled) 0.dp else 16.dp)
      .fillMaxWidth()
  ) {
    val sparklineWidthModifier = if (isDesignSystemV2Enabled) {
      Modifier.weight(0.27f, fill = false)
    } else {
      Modifier.weight(0.4f, fill = false)
    }

    if (!isDesignSystemV2Enabled) {
      // title + updated at timestamp
      Row(verticalAlignment = Alignment.Top) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(Res.drawable.bitcoin_orange),
            contentDescription = null,
            modifier = Modifier.size(Accessory.value.dp)
          )

          Spacer(modifier = Modifier.width(4.dp))

          Label(
            text = stringResource(Res.string.bitcoin_price_card_title),
            type = LabelType.Body3Bold,
            treatment = LabelTreatment.Unspecified,
            color = WalletTheme.colors.bitcoinPrimary
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .padding(2.dp)
              .weight(1f, fill = false),
            contentAlignment = Alignment.CenterStart
          ) {
            Label(
              model = LabelModel.StringModel(model.lastUpdated),
              type = LabelType.Body4Regular,
              treatment = LabelTreatment.SecondaryDark,
              alignment = TextAlign.End
            )
          }

          Spacer(modifier = Modifier.requiredSize(1.dp))

          Icon(
            icon = Icon.SmallIconCaretRight,
            size = Subtract,
            tint = IconTint.On30
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = if (isDesignSystemV2Enabled) Alignment.CenterVertically else Alignment.Bottom,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // bitcoin price + value change
      PriceAndValueChangeColumn(
        model = model,
        isDesignSystemV2Enabled = isDesignSystemV2Enabled
      )

      Spacer(modifier = Modifier.weight(0.1f, fill = false))

      // price chart
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .then(sparklineWidthModifier)
          .height(if (isDesignSystemV2Enabled) 60.dp else 70.dp)
          .padding(top = if (isDesignSystemV2Enabled) 0.dp else 16.dp, end = 6.dp)
      ) {
        val placeholderAlpha by animateFloatAsState(
          label = "placeholder-visibility",
          targetValue = if (model.data.isEmpty()) 1f else 0f
        )
        val sparklineAlpha by animateFloatAsState(
          label = "sparkline-visibility",
          targetValue = if (model.data.isEmpty()) 0f else 1f
        )
        Image(
          imageVector = vectorResource(Res.drawable.sparkline_placeholder),
          contentDescription = null,
          modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .alpha(placeholderAlpha)
        )

        if (model.data.isNotEmpty()) {
          PriceChart(
            dataPoints = model.data,
            range = ChartRange.DAY,
            colorSparkLine = if (isDesignSystemV2Enabled) {
              WalletTheme.colors.foreground
            } else {
              WalletTheme.colors.foreground.copy(alpha = 0.1f)
            },
            sparkLineMode = true,
            showSparkLineEndPoint = !isDesignSystemV2Enabled,
            lineCornerRadius = if (isDesignSystemV2Enabled) 12.dp else 6.dp,
            yAxisIntervals = 10,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer(
                alpha = sparklineAlpha,
                clip = false,
                compositingStrategy = CompositingStrategy.ModulateAlpha
              )
          )
        }
      }
    }
  }
}

@Composable
private fun PriceAndValueChangeColumn(
  model: BitcoinPrice,
  isDesignSystemV2Enabled: Boolean,
) {
  Column(
    modifier = Modifier
      .wrapContentSize(),
    verticalArrangement = Arrangement.Bottom
  ) {
    if (isDesignSystemV2Enabled) {
      Label(
        text = stringResource(Res.string.bitcoin_price_card_title),
        type = LabelType.Body2Regular,
        treatment = LabelTreatment.Primary
      )
    }

    val priceLabelType = if (isDesignSystemV2Enabled) LabelType.Body1Regular else LabelType.Body1Bold

    Box(
      modifier = Modifier
        .loadingScrim(model.isLoading),
      contentAlignment = Alignment.BottomStart
    ) {
      MeasureWithoutPlacement {
        Label(
          model = LabelModel.StringModel("$000,000.00"),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          maxLines = 1
        )
      }

      if (isDesignSystemV2Enabled && model.priceValue != null) {
        AnimatedAmountAutoResizedLabel(
          amount = AnimatedAmount(
            text = model.price,
            value = model.priceValue,
            animationKey = model.priceAnimationKey
          ),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          animate = true,
          animationLabel = "BitcoinPriceCardPrice",
          minTextSize = bitcoinPriceCardMinTextSize()
        )
      } else {
        Label(
          model = LabelModel.StringModel(model.price),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          maxLines = 1
        )
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .loadingScrim(model.isLoading)
    ) {
      val animatedPriceDirection by animateFloatAsState(
        label = "price-direction",
        targetValue = model.priceDirection.orientation,
        animationSpec = tween(200)
      )
      Image(
        imageVector = vectorResource(Res.drawable.small_icon_arrow_up),
        contentDescription = null,
        colorFilter = ColorFilter.tint(WalletTheme.colors.foreground60),
        modifier = Modifier
          .size(16.dp)
          .rotate(animatedPriceDirection)
      )

      Box(
        contentAlignment = Alignment.BottomStart
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = LabelModel.StringModel("50.00% Today"),
            type = LabelType.Body3Regular,
            treatment = LabelTreatment.Secondary,
            maxLines = 1
          )
        }
        Label(
          model = LabelModel.StringModel(model.priceChange),
          type = LabelType.Body3Regular,
          treatment = LabelTreatment.Secondary,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun bitcoinPriceCardMinTextSize(): TextUnit {
  return WalletTheme.labelStyle(
    type = LabelType.Body2Regular,
    treatment = LabelTreatment.Primary
  ).fontSize
}

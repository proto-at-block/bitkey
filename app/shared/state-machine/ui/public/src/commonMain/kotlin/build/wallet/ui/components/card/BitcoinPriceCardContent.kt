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
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.*
import build.wallet.pricechart.ui.PriceChart
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Subtract
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun BitcoinPriceContent(model: BitcoinPrice) {
  Column(
    modifier = Modifier
      .padding(bottom = 16.dp)
      .fillMaxWidth()
  ) {
    // title + updated at timestamp
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

      Spacer(modifier = Modifier.weight(1f))

      Box(
        modifier = Modifier
          .loadingScrim(model.isLoading),
        contentAlignment = Alignment.CenterEnd
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = LabelModel.StringModel("Updated 12:00am"),
            type = LabelType.Body4Regular,
            treatment = LabelTreatment.Disabled
          )
        }
        Label(
          text = model.lastUpdated,
          type = LabelType.Body4Regular,
          treatment = LabelTreatment.Disabled
        )
      }

      Spacer(modifier = Modifier.width(1.dp))

      Icon(
        icon = Icon.SmallIconCaretRight,
        size = Subtract,
        tint = IconTint.On30
      )
    }
    Row(verticalAlignment = Alignment.Bottom) {
      // bitcoin price + value change
      Column(
        modifier = Modifier
          .weight(0.3f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
      ) {
        Box(
          modifier = Modifier
            .loadingScrim(model.isLoading),
          contentAlignment = Alignment.BottomStart
        ) {
          MeasureWithoutPlacement {
            Label(
              model = LabelModel.StringModel("$00,000.00"),
              type = LabelType.Body1Bold,
              treatment = LabelTreatment.Primary
            )
          }

          Label(
            model = LabelModel.StringModel(model.price),
            type = LabelType.Body1Bold,
            treatment = LabelTreatment.Primary
          )
        }

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
                treatment = LabelTreatment.Secondary
              )
            }
            Label(
              model = LabelModel.StringModel(model.priceChange),
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary
            )
          }
        }
      }

      Spacer(modifier = Modifier.weight(0.1f))

      // price chart
      Column(
        modifier = Modifier
          .weight(0.4f)
          .height(70.dp)
          .padding(top = 16.dp, end = 6.dp)
      ) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
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
              colorSparkLine = WalletTheme.colors.foreground.copy(alpha = 0.1f),
              sparkLineMode = true,
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
}
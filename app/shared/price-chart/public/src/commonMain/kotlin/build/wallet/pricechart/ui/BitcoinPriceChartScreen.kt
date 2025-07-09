package build.wallet.pricechart.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitcoin_orange
import bitkey.ui.framework_public.generated.resources.small_icon_arrow_up
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.PriceDirection
import build.wallet.pricechart.SelectedPointData
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun BitcoinPriceChartScreen(model: BitcoinPriceDetailsBodyModel) {
  SelectedPointDetails(
    isLoading = model.isLoading,
    data = model.selectedPointData as? SelectedPointData.BtcPrice
  )

  val alpha by animateFloatAsState(
    targetValue = if (model.isLoading) 0f else 1f
  )
  if (model.failedToLoad) {
    LoadingErrorMessage()
  } else if (model.isLoading || model.data.isEmpty()) {
    Spacer(modifier = Modifier.fillMaxSize())
  } else {
    Column(
      modifier = Modifier
        .fillMaxSize()
    ) {
      SelectedPointTimeDisplay(
        selectedPointTimeText = model.selectedPointTimestamp
      )
      Spacer(modifier = Modifier.size(6.dp))
      PriceChart(
        dataPoints = model.data,
        range = model.range,
        initialSelectedPoint = model.selectedPoint,
        onPointSelected = model.onPointSelected,
        colorPrimary = WalletTheme.colors.bitcoinPrimary,
        formatYLabel = model.formatFiatValue,
        modifier = Modifier
          .fillMaxSize()
          .alpha(alpha)
      )
    }
  }
}

/**
 * Displays the [selectedPointTimeText] when it's not empty and
 * gracefully animates it away when the value is cleared.
 */
@Composable
internal fun SelectedPointTimeDisplay(selectedPointTimeText: String?) {
  val selectedTimestampAlpha by animateFloatAsState(
    targetValue = if (selectedPointTimeText == null) 0f else 1f
  )
  val notBlankSelectedPointTimeText by produceState("", selectedPointTimeText) {
    // don't clear selectedPointTimeText to preserve animation
    selectedPointTimeText?.let { value = it }
  }

  Label(
    model = LabelModel.StringModel(notBlankSelectedPointTimeText),
    treatment = LabelTreatment.Secondary,
    type = LabelType.Body3Medium,
    modifier = Modifier.alpha(selectedTimestampAlpha)
  )
}

/**
 * Displays the currently selected chart point or the latest point
 * if nothing is selected.
 */
@Composable
private fun SelectedPointDetails(
  isLoading: Boolean,
  data: SelectedPointData.BtcPrice?,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top)
    ) {
      Label(
        model = LabelModel.StringModel("Bitcoin price"),
        type = LabelType.Body3Regular,
        treatment = LabelTreatment.Secondary
      )
      val alpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f
      )
      val selectedAlpha by animateFloatAsState(
        targetValue = if (data?.isUserSelected == true) 0f else 1f
      )

      Box(
        modifier = Modifier
          .loadingScrim(isLoading)
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = LabelModel.StringModel("$ 00000.00"),
            type = LabelType.Title1
          )
        }
        Label(
          model = LabelModel.StringModel(data?.primaryText.orEmpty()),
          type = LabelType.Title1,
          treatment = LabelTreatment.Primary
        )
      }
      Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .loadingScrim(isLoading)
      ) {
        val retainedOrientation by produceState(
          (data?.direction ?: PriceDirection.STABLE).orientation,
          data
        ) {
          value = data?.direction?.orientation ?: value
        }

        val animatedPriceDirection by animateFloatAsState(
          targetValue = retainedOrientation,
          animationSpec = tween(200)
        )
        Image(
          imageVector = vectorResource(Res.drawable.small_icon_arrow_up),
          contentDescription = null,
          colorFilter = ColorFilter.tint(WalletTheme.colors.foreground60),
          modifier = Modifier
            .size(16.dp)
            // do not apply rotation animation when first appearing
            .thenIf(alpha < 1f) {
              Modifier.rotate(retainedOrientation)
            }
            .thenIf(alpha == 1f) {
              Modifier.rotate(animatedPriceDirection)
            }
        )
        Box {
          MeasureWithoutPlacement {
            // size the loader based on the expected value size, not displayed to user
            Label(
              model = LabelModel.StringModel("50.00% Past year"),
              type = LabelType.Body3Regular
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Label(
              model = LabelModel.StringModel(data?.secondaryText.orEmpty()),
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary
            )
            Label(
              model = LabelModel.StringModel(data?.secondaryTimePeriodText.orEmpty()),
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary,
              modifier = Modifier.alpha(selectedAlpha)
            )
          }
        }
      }
    }
    Icon(
      imageVector = vectorResource(Res.drawable.bitcoin_orange),
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = Color.Unspecified
    )
  }
}

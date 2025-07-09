package build.wallet.pricechart.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.wallet.pricechart.BalanceAt
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.SelectedPointData
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Body3Regular
import build.wallet.ui.tokens.LabelType.Title2

/**
 * Displays coming soon details and animated icon for Your balance screen.
 */
@Composable
internal fun BalanceHistoryScreen(model: BitcoinPriceDetailsBodyModel) {
  val dataRowAlpha by animateFloatAsState(
    targetValue = if (model.isLoading || !model.data.isEmpty()) 1f else 0f,
    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
  )
  SelectedPointDetails(
    isLoading = model.isLoading,
    fiatCurrencyCode = model.fiatCurrencyCode,
    data = model.selectedPointData as? SelectedPointData.Balance,
    modifier = Modifier.alpha(dataRowAlpha)
  )

  val alpha by animateFloatAsState(
    targetValue = if (model.isLoading) 0f else 1f
  )
  when {
    model.failedToLoad -> LoadingErrorMessage()
    model.isLoading -> Spacer(modifier = Modifier.fillMaxSize())
    model.data.isEmpty() -> {
      Column {
        EmptyWalletMessage(
          onBuy = model.onBuy,
          onTransfer = model.onTransfer
        )
        Spacer(Modifier.weight(1f))
      }
    }
    else -> {
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
          colorPrimary = WalletTheme.colors.yourBalancePrimary,
          formatYLabel = model.formatFiatValue,
          extractSecondaryYValue = { (it as? BalanceAt)?.balance ?: 0.0 },
          modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
        )
      }
    }
  }
}

@Composable
private fun EmptyWalletMessage(
  onBuy: () -> Unit,
  onTransfer: () -> Unit,
) {
  Column(
    modifier = Modifier
      .background(WalletTheme.colors.background)
      .clip(RoundedCornerShape(20.dp))
      .border(
        BorderStroke(2.dp, WalletTheme.colors.foreground10),
        RoundedCornerShape(20.dp)
      )
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    IconImage(
      model = IconModel(
        Icon.Insights,
        IconSize.Medium,
        IconBackgroundType.Circle(IconSize.Avatar)
      )
    )
    Label(
      model = StringModel("Add bitcoin to track performance"),
      type = Title2,
      treatment = LabelTreatment.Primary
    )
    Label(
      model = StringModel("The value of your balance over time will be graphed and displayed here."),
      type = Body3Regular,
      alignment = TextAlign.Center,
      treatment = LabelTreatment.Secondary
    )
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
        .padding(horizontal = 8.dp)
    ) {
      Button(
        modifier = Modifier.weight(0.5f),
        text = "Transfer",
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onTransfer)
      )
      Spacer(Modifier.width(16.dp))
      Button(
        modifier = Modifier.weight(0.5f),
        text = "Buy",
        treatment = ButtonModel.Treatment.Primary,
        onClick = StandardClick(onBuy)
      )
    }
  }
}

@Composable
private fun SelectedPointDetails(
  isLoading: Boolean,
  fiatCurrencyCode: String?,
  data: SelectedPointData.Balance?,
  modifier: Modifier = Modifier,
) {
  val selectedAlpha by animateFloatAsState(
    targetValue = if (data?.isUserSelected == true) 0f else 1f
  )
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Left column - Fiat balance
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start)
      ) {
        Spacer(
          modifier = Modifier
            .size(14.dp)
            .background(WalletTheme.colors.yourBalancePrimary, CircleShape)
        )
        Label(
          model = StringModel("Value ($fiatCurrencyCode)"),
          type = Body3Regular,
          treatment = LabelTreatment.Secondary
        )
      }

      AutoSizingLabel(
        text = data?.primaryFiatText.orEmpty(),
        maxType = LabelType.Title1,
        minType = LabelType.Body2Medium,
        treatment = LabelTreatment.Primary
      )
      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
          .loadingScrim(isLoading)
          .alpha(selectedAlpha)
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = StringModel("+50.00% Past year"),
            type = Body3Regular
          )
        }
        Label(
          model = StringModel(data?.secondaryFiatText.orEmpty()),
          type = LabelType.Body4Regular,
          treatment = LabelTreatment.Secondary
        )
      }
    }

    // Right column - Bitcoin balance
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
      horizontalAlignment = Alignment.End
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Spacer(
          modifier = Modifier
            .size(14.dp)
            .background(WalletTheme.colors.chartElement, RoundedCornerShape(4.dp))
        )
        Label(
          model = StringModel("Bitcoin balance"),
          type = Body3Regular,
          treatment = LabelTreatment.Secondary
        )
      }
      AutoSizingLabel(
        text = data?.primaryBtcText.orEmpty(),
        maxType = LabelType.Title1,
        minType = LabelType.Body2Medium,
        treatment = LabelTreatment.Primary,
        alignment = TextAlign.End
      )
      Box(
        modifier = Modifier
          .loadingScrim(isLoading)
          .alpha(selectedAlpha)
          .fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = StringModel("+50.00% Past year"),
            type = Body3Regular
          )
        }
        Label(
          model = StringModel(data?.secondaryBtcText.orEmpty()),
          type = LabelType.Body4Regular,
          treatment = LabelTreatment.Secondary,
          alignment = TextAlign.End
        )
      }
    }
  }
}

@Composable
private fun AutoSizingLabel(
  text: String,
  maxType: LabelType,
  minType: LabelType,
  modifier: Modifier = Modifier,
  treatment: LabelTreatment = LabelTreatment.Primary,
  alignment: TextAlign = TextAlign.Start,
) {
  var currentType by remember(text) { mutableStateOf(maxType) }

  Label(
    model = StringModel(text),
    type = currentType,
    treatment = treatment,
    alignment = alignment,
    modifier = modifier,
    maxLines = 1,
    overflow = TextOverflow.Clip,
    onTextLayout = { textLayoutResult: TextLayoutResult ->
      // If text is clipped (doesn't fit in one line) and we haven't reached minimum size
      if (textLayoutResult.hasVisualOverflow && currentType != minType) {
        // Try next smaller font size
        currentType = when (currentType) {
          LabelType.Title1 -> LabelType.Body1Medium
          LabelType.Body1Medium -> LabelType.Body2Medium
          else -> minType
        }
      }
    }
  )
}

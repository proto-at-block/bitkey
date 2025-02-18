package build.wallet.pricechart.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.bitcoin_orange
import bitkey.shared.ui_core_public.generated.resources.small_icon_arrow_up
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.ChartHistory
import build.wallet.pricechart.ChartType
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.label.textStyle
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun BitcoinPriceChartScreen(
  modifier: Modifier = Modifier,
  model: BitcoinPriceDetailsBodyModel,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Toolbar(
      modifier = Modifier.fillMaxWidth(),
      model = model.toolbarModel
    )

    ChartTypeSelector(
      selectedType = model.type,
      onChartTypeSelected = model.onChartTypeSelected
    )

    Spacer(modifier = Modifier.height(4.dp))

    AnimatedContent(
      targetState = model.type,
      modifier = Modifier.weight(1f),
      transitionSpec = {
        fadeIn(animationSpec = tween(200, delayMillis = 90))
          .togetherWith(fadeOut(animationSpec = tween(90)))
      }
    ) { type ->
      when (type) {
        ChartType.BALANCE -> ComingSoonScreen()
        ChartType.BTC_PRICE -> {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
          ) {
            SelectedPointDetails(
              isLoading = model.isLoading,
              isUserSelected = model.selectedPoint != null,
              selectedPointText = model.selectedPointPrimaryText.orEmpty(),
              selectedPointDiffText = model.selectedPointSecondaryText.orEmpty(),
              selectedPriceDirection = model.selectedPriceDirection,
              selectedPointPeriodText = model.selectedPointPeriodText.orEmpty()
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
                  selectedPointTimeText = model.selectedPointChartText
                )
                Spacer(modifier = Modifier.size(6.dp))
                PriceChart(
                  dataPoints = model.data,
                  initialSelectedPoint = model.selectedPoint,
                  onPointSelected = model.onPointSelected,
                  onPointDeselected = { model.onPointSelected(null) },
                  colorPrimary = WalletTheme.colors.bitcoinPrimary,
                  formatYLabel = model.formatFiatValue,
                  modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(36.dp))

    ChartHistorySelector(
      selectedHistory = model.history,
      onChartHistorySelected = model.onChartHistorySelected
    )

    Spacer(modifier = Modifier.height(18.dp))
  }
}

/**
 * Displays the [selectedPointTimeText] when it's not empty and
 * gracefully animates it away when the value is cleared.
 */
@Composable
private fun SelectedPointTimeDisplay(selectedPointTimeText: String?) {
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
  isUserSelected: Boolean,
  selectedPointText: String,
  selectedPointDiffText: String,
  selectedPriceDirection: PriceDirection,
  selectedPointPeriodText: String,
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
        targetValue = if (isUserSelected) 0f else 1f
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
          model = LabelModel.StringModel(selectedPointText),
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
        val animatedPriceDirection by animateFloatAsState(
          targetValue = selectedPriceDirection.orientation,
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
              Modifier.rotate(selectedPriceDirection.orientation)
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
              model = LabelModel.StringModel(selectedPointDiffText),
              type = LabelType.Body3Regular,
              treatment = LabelTreatment.Secondary
            )
            Label(
              model = LabelModel.StringModel(selectedPointPeriodText),
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

/**
 * A multi-value selector that animates a color changing indicator
 * behind the active selection. Contains multiple [ChartTypeButton]s.
 */
@Composable
private fun ChartTypeSelector(
  selectedType: ChartType,
  onChartTypeSelected: (ChartType) -> Unit,
) {
  val updatedSelectedType by rememberUpdatedState(selectedType)
  val updatedOnChartTypeSelected by rememberUpdatedState(onChartTypeSelected)
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 6.dp, vertical = 4.dp)
      .border(
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(50.dp)
      )
  ) {
    val targetOffsetX by remember {
      derivedStateOf {
        when (updatedSelectedType) {
          ChartType.BTC_PRICE -> 0f
          ChartType.BALANCE -> constraints.maxWidth / 2f
        }
      }
    }
    val bitcoinPrimaryColor = WalletTheme.colors.bitcoinPrimary
    val yourBalancePrimaryColor = WalletTheme.colors.yourBalancePrimary
    val targetColor by remember {
      derivedStateOf {
        when (updatedSelectedType) {
          ChartType.BTC_PRICE -> bitcoinPrimaryColor
          ChartType.BALANCE -> yourBalancePrimaryColor
        }
      }
    }
    val animatedOffsetX by animateFloatAsState(targetOffsetX)
    val animatedColor by animateColorAsState(targetColor)

    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        // draw active background pill shape
        .drawWithCache {
          val padding = 8.dp.toPx()
          val size = Size(
            (size.width / 2) - padding,
            size.height - padding
          )
          val offsetPadding = padding / 2
          val offset = Offset(animatedOffsetX + offsetPadding, offsetPadding)
          val radiusPx = 50.dp.toPx()
          val cornerRadius = CornerRadius(radiusPx, radiusPx)
          onDrawBehind {
            drawRoundRect(
              color = animatedColor,
              size = size,
              topLeft = offset,
              cornerRadius = cornerRadius
            )
          }
        }
    ) {
      // show each chart option
      ChartType.entries.forEach { chartType ->
        ChartTypeButton(
          text = stringResource(chartType.label),
          isSelected = updatedSelectedType == chartType,
          onClick = { updatedOnChartTypeSelected(chartType) },
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

/**
 * A button to be displayed in a [ChartTypeSelector] group.
 */
@Composable
private fun ChartTypeButton(
  text: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clickable(
        onClick = onClick,
        // disable default click animation
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ),
    contentAlignment = Alignment.Center
  ) {
    if (isSelected) {
      Label(
        text = text,
        style = WalletTheme.textStyle(
          type = LabelType.Body3Bold,
          treatment = LabelTreatment.Unspecified,
          textColor = Color.White
        ),
        modifier = Modifier
          .padding(vertical = 8.dp)
      )
    } else {
      Label(
        model = LabelModel.StringModel(text),
        type = LabelType.Body2Regular,
        treatment = LabelTreatment.Secondary,
        modifier = Modifier
          .padding(vertical = 8.dp)
      )
    }
  }
}

/**
 * Displays a row of buttons to select a [ChartHistory].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChartHistorySelector(
  selectedHistory: ChartHistory,
  onChartHistorySelected: (ChartHistory) -> Unit = {},
) {
  val updatedSelectedHistory by rememberUpdatedState(selectedHistory)
  val updatedOnChartHistorySelected by rememberUpdatedState(onChartHistorySelected)
  Row(
    modifier = Modifier
      .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ChartHistory.entries.forEach { entry ->
      val isSelected by remember {
        derivedStateOf { entry == updatedSelectedHistory }
      }
      val circleColor by animateColorAsState(
        targetValue = if (isSelected) {
          WalletTheme.colors.calloutDefaultBackground
        } else {
          WalletTheme.colors.calloutDefaultBackground.copy(alpha = 0f)
        }
      )
      val circleRadius by animateDpAsState(
        targetValue = if (isSelected) 22.dp else 18.dp
      )
      Box(
        modifier = Modifier
          .clickable(
            // clip ripple toc fill background shape
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) { updatedOnChartHistorySelected(entry) }
          .padding(8.dp),
        contentAlignment = Alignment.Center
      ) {
        Label(
          modifier = Modifier
            .drawBehind {
              drawCircle(
                color = circleColor,
                radius = circleRadius.toPx()
              )
            },
          text = stringResource(entry.label),
          style = WalletTheme.textStyle(
            type = if (isSelected) LabelType.Body2Bold else LabelType.Body3Regular,
            treatment = if (isSelected) LabelTreatment.Primary else LabelTreatment.Secondary,
            textColor = WalletTheme.colors.foreground
          ).copy(lineHeight = TextUnit.Unspecified)
        )
        if (!isSelected) {
          Label(
            text = stringResource(entry.label),
            modifier = Modifier.semantics { invisibleToUser() },
            style = WalletTheme.textStyle(
              type = LabelType.Body2Bold,
              treatment = LabelTreatment.Unspecified,
              textColor = Color.Transparent
            )
          )
        }
      }
    }
  }
}

@Composable
private fun LoadingErrorMessage() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      modifier = Modifier,
      icon = build.wallet.statemachine.core.Icon.SmallIconCloudError,
      size = IconSize.Large,
      color = WalletTheme.colors.foreground30
    )
    Label(
      model = LabelModel.StringModel("It looks like something went wrong"),
      type = LabelType.Body3Regular,
      treatment = LabelTreatment.Disabled
    )
  }
}

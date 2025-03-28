package build.wallet.pricechart.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ChartType
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.textStyle
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChartScreen(
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
        fadeIn(tween(200, delayMillis = 90))
          .togetherWith(fadeOut(tween(90)))
      }
    ) { type ->
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
      ) {
        when (type) {
          ChartType.BALANCE -> {
            if (model.isBalanceHistoryEnabled || LocalIsPreviewTheme.current) {
              BalanceHistoryScreen(model = model)
            } else {
              ComingSoonScreen()
            }
          }
          ChartType.BTC_PRICE -> BitcoinPriceChartScreen(model = model)
        }
      }
    }

    Spacer(modifier = Modifier.height(36.dp))

    ChartHistorySelector(
      selectedHistory = model.range,
      onChartHistorySelected = model.onChartRangeSelected
    )

    Spacer(modifier = Modifier.height(18.dp))
  }
}

@Composable
internal fun LoadingErrorMessage() {
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
        border = BorderStroke(1.dp, WalletTheme.colors.chartElement),
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
 * Displays a row of buttons to select a [ChartRange].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChartHistorySelector(
  selectedHistory: ChartRange,
  onChartHistorySelected: (ChartRange) -> Unit = {},
) {
  val updatedSelectedHistory by rememberUpdatedState(selectedHistory)
  val updatedOnChartHistorySelected by rememberUpdatedState(onChartHistorySelected)
  Row(
    modifier = Modifier
      .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ChartRange.entries.forEach { entry ->
      val isSelected by remember {
        derivedStateOf { entry == updatedSelectedHistory }
      }
      val circleColor by animateColorAsState(
        targetValue = if (isSelected) {
          WalletTheme.colors.subtleBackground
        } else {
          WalletTheme.colors.subtleBackground.copy(alpha = 0f)
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

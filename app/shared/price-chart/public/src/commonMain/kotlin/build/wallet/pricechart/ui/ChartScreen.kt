package build.wallet.pricechart.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.collections.immutable.toImmutableList
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

    CircularTabRow(
      items = ChartType.entries.map { stringResource(it.label) }.toImmutableList(),
      selectedItemIndex = model.type.ordinal,
      onClick = { model.onChartTypeSelected(ChartType.entries[it]) }
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

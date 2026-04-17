package build.wallet.ui.components.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
internal fun amountEntryBackgroundColor(): Color =
  if (LocalDesignSystemUpdatesEnabled.current) {
    WalletTheme.colors.subtleBackground
  } else {
    WalletTheme.colors.background
  }

@Composable
internal fun AmountEntryToolbar(
  model: ToolbarModel,
  modifier: Modifier = Modifier,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val backgroundColor = amountEntryBackgroundColor()

  if (isDesignSystemV2Enabled) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(
            minHeight = AmountEntryToolbarTopPadding +
              AmountEntryToolbarHeight +
              AmountEntryToolbarBottomPadding
          )
          .background(backgroundColor)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              top = AmountEntryToolbarTopPadding,
              bottom = AmountEntryToolbarBottomPadding
            )
        ) {
          AmountEntryToolbarContent(
            modifier = modifier,
            model = model,
            isDesignSystemV2Enabled = true
          )
        }
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(AmountEntryToolbarBottomGradientHeight)
          .background(
            brush =
              Brush.verticalGradient(
                colors =
                  listOf(
                    backgroundColor,
                    backgroundColor.copy(alpha = 0.65f),
                    Color.Transparent
                  )
              )
          )
      )
    }
  } else {
    Toolbar(
      modifier = modifier,
      leadingContent = {
        model.leadingAccessory?.let {
          ToolbarAccessory(it)
        }
      },
      middleContent = {
        AmountEntryToolbarMiddleContent(
          model = model,
          isDesignSystemV2Enabled = false
        )
      },
      trailingContent = {
        model.trailingAccessory?.let {
          ToolbarAccessory(it)
        }
      }
    )
  }
}

@Composable
private fun AmountEntryToolbarContent(
  modifier: Modifier = Modifier,
  model: ToolbarModel,
  isDesignSystemV2Enabled: Boolean,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = AmountEntryToolbarHeight)
  ) {
    Box(modifier = Modifier.align(Alignment.CenterStart)) {
      model.leadingAccessory?.let {
        ToolbarAccessory(it)
      }
    }
    Box(modifier = Modifier.align(Alignment.Center)) {
      AmountEntryToolbarMiddleContent(
        model = model,
        isDesignSystemV2Enabled = isDesignSystemV2Enabled
      )
    }
    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
      model.trailingAccessory?.let {
        ToolbarAccessory(it)
      }
    }
  }
}

@Composable
private fun AmountEntryToolbarMiddleContent(
  model: ToolbarModel,
  isDesignSystemV2Enabled: Boolean,
) {
  model.middleAccessory?.let { middleAccessory ->
    Column(horizontalAlignment = CenterHorizontally) {
      Label(
        text = middleAccessory.title,
        type = if (isDesignSystemV2Enabled) LabelType.Body2Regular else LabelType.Title2
      )
      middleAccessory.subtitle?.let {
        Label(
          text = it,
          type = if (isDesignSystemV2Enabled) LabelType.Body3Regular else LabelType.Title3,
          treatment = Secondary
        )
      }
    }
  }
}

private val AmountEntryToolbarTopPadding = 8.dp
private val AmountEntryToolbarHeight = 48.dp
private val AmountEntryToolbarBottomPadding = 8.dp
private val AmountEntryToolbarBottomGradientHeight = 20.dp

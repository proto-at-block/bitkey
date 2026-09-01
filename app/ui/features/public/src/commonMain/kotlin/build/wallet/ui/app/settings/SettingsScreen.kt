package build.wallet.ui.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.CollapsibleToolbar
import build.wallet.ui.components.toolbar.CollapsibleToolbarReservedHeight
import build.wallet.ui.components.toolbar.CollapsibleToolbarTitleCollapseRange
import build.wallet.ui.components.toolbar.LinearInlineTitleAlpha
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.inlineTitle
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
  model: SettingsBodyModel,
) {
  BackHandler(onBack = model.onBack)
  val scrollState = rememberScrollState()
  val collapseRangePx = with(LocalDensity.current) { CollapsibleToolbarTitleCollapseRange.toPx() }
  val title = model.toolbarModel.inlineTitle?.title ?: "Settings"

  val collapseProgress by remember(scrollState, collapseRangePx) {
    derivedStateOf {
      if (collapseRangePx <= 0f) {
        0f
      } else {
        (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(WalletTheme.colors.background)
      .imePadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = SETTINGS_HORIZONTAL_PADDING)
    ) {
      // Reserved space for the fixed top toolbar.
      Spacer(modifier = Modifier.height(CollapsibleToolbarReservedHeight))
      Label(
        modifier = Modifier
          .padding(top = SETTINGS_LARGE_TITLE_TOP_SPACING)
          .alpha(1f - collapseProgress),
        text = title,
        type = LabelType.Display3
      )
      Column(
        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
      ) {
        for (sectionModel in model.sectionModels) {
          SettingsSection(model = sectionModel)
        }
      }
    }

    CollapsibleToolbar(
      toolbarModel = model.toolbarModel,
      title = model.toolbarModel.inlineTitle?.title ?: title,
      collapseProgress = collapseProgress,
      horizontalPadding = SETTINGS_HORIZONTAL_PADDING,
      inlineTitleAlpha = LinearInlineTitleAlpha
    )
  }
}

@Composable
private fun SettingsSection(
  model: SettingsBodyModel.SectionModel,
) {
  Column {
    // Section title
    Label(
      modifier = Modifier.padding(top = 8.dp),
      text = model.sectionHeaderTitle,
      treatment = LabelTreatment.Secondary,
      type = LabelType.Body3Mono
    )

    // Section rows
    for (rowModel in model.rowModels) {
      ListItem(
        title = rowModel.title,
        contentSpacing = 12.dp,
        titleType = LabelType.Body2MonoCaps,
        titleTreatment = if (rowModel.isDisabled) LabelTreatment.Disabled else LabelTreatment.Primary,
        leadingAccessory =
          ListItemAccessory.IconAccessory(
            model =
              IconModel(
                icon = rowModel.icon,
                iconSize = IconSize.Accessory,
                iconBackgroundType = Transient,
                iconTint = if (rowModel.isDisabled) IconTint.On10 else null
              )
          ),
        trailingAccessory =
          ListItemAccessory.drillIcon(
            tint = IconTint.On30,
            iconSize = IconSize.Accessory
          ).takeIf { !rowModel.isDisabled },
        onClick = rowModel.onClick,
        coachmarkLabel = rowModel.coachmarkLabelModel
      )
      Divider(
        color = WalletTheme.colors.subtleBackground
      )
    }
  }
}

private val SETTINGS_HORIZONTAL_PADDING: Dp = 20.dp
private val SETTINGS_LARGE_TITLE_TOP_SPACING: Dp = 24.dp

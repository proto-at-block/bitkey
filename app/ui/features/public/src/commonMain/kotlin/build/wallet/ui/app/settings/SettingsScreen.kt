package build.wallet.ui.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
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
  val collapseRangePx = with(LocalDensity.current) { SETTINGS_TITLE_COLLAPSE_RANGE.toPx() }
  val title = model.toolbarModel.middleAccessory?.title ?: "Settings"

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
      Spacer(modifier = Modifier.height(SETTINGS_TOP_PADDING + SETTINGS_TOOLBAR_HEIGHT + SETTINGS_TOOLBAR_BOTTOM_PADDING))
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

    SettingsCollapsibleToolbar(
      title = title,
      collapseProgress = collapseProgress,
      middleAccessory = model.toolbarModel.middleAccessory,
      leadingAccessory = model.toolbarModel.leadingAccessory,
      trailingAccessory = model.toolbarModel.trailingAccessory
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

@Composable
private fun SettingsCollapsibleToolbar(
  title: String,
  collapseProgress: Float,
  middleAccessory: ToolbarMiddleAccessoryModel?,
  leadingAccessory: build.wallet.ui.model.toolbar.ToolbarAccessoryModel?,
  trailingAccessory: build.wallet.ui.model.toolbar.ToolbarAccessoryModel?,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        SETTINGS_TOP_PADDING +
          SETTINGS_TOOLBAR_HEIGHT +
          SETTINGS_TOOLBAR_BOTTOM_PADDING +
          SETTINGS_TOOLBAR_BOTTOM_GRADIENT_HEIGHT
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(SETTINGS_TOP_PADDING + SETTINGS_TOOLBAR_HEIGHT + SETTINGS_TOOLBAR_BOTTOM_PADDING)
        .background(WalletTheme.colors.background)
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = SETTINGS_TOP_PADDING,
            start = SETTINGS_HORIZONTAL_PADDING,
            end = SETTINGS_HORIZONTAL_PADDING
          )
          .fillMaxWidth()
          .height(SETTINGS_TOOLBAR_HEIGHT)
      ) {
        Toolbar(
          model = build.wallet.ui.model.toolbar.ToolbarModel(
            leadingAccessory = leadingAccessory,
            middleAccessory = null,
            trailingAccessory = trailingAccessory
          ),
          showDesignSystemChrome = false
        )
        val hasLeadingAccessory = leadingAccessory != null
        Label(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              start = if (hasLeadingAccessory) SETTINGS_INLINE_TITLE_START_WITH_LEADING else 0.dp,
              end = SETTINGS_INLINE_TITLE_END_PADDING
            )
            .align(Alignment.CenterStart)
            .alpha(collapseProgress),
          text = middleAccessory?.title ?: title,
          type = LabelType.Title2
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(SETTINGS_TOOLBAR_BOTTOM_GRADIENT_HEIGHT)
        .align(Alignment.BottomCenter)
        .background(
          brush =
            Brush.verticalGradient(
              colors =
                listOf(
                  WalletTheme.colors.background,
                  WalletTheme.colors.background.copy(alpha = 0.65f),
                  Color.Transparent
                )
            )
        )
    )
  }
}

private val SETTINGS_TOP_PADDING: Dp = 8.dp
private val SETTINGS_HORIZONTAL_PADDING: Dp = 20.dp
private val SETTINGS_TOOLBAR_HEIGHT: Dp = 48.dp
private val SETTINGS_TOOLBAR_BOTTOM_PADDING: Dp = 8.dp
private val SETTINGS_TOOLBAR_BOTTOM_GRADIENT_HEIGHT: Dp = 20.dp
private val SETTINGS_LARGE_TITLE_TOP_SPACING: Dp = 24.dp
private val SETTINGS_INLINE_TITLE_START_WITH_LEADING: Dp = 56.dp
private val SETTINGS_INLINE_TITLE_END_PADDING: Dp = 56.dp
private val SETTINGS_TITLE_COLLAPSE_RANGE: Dp = 120.dp

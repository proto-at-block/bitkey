package build.wallet.statemachine.money.currency

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.AppearanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

class AppearancePreferenceBodyModel(
  override val onBack: () -> Unit,
  val moneyHomeHero: MoneyHomeHeroModel,
  val selectedSection: AppearanceSection,
  val onSectionSelected: (AppearanceSection) -> Unit,
  val themePreferenceString: String,
  val onThemePreferenceClick: () -> Unit,
  val fiatCurrencyPreferenceString: String,
  val onFiatCurrencyPreferenceClick: () -> Unit,
  val bitcoinDisplayPreferenceString: String,
  val isBitcoinPriceCardEnabled: Boolean = false,
  val defaultTimeScalePreferenceString: String,
  val onDefaultTimeScalePreferenceClick: () -> Unit,
  val isHideBalanceEnabled: Boolean = false,
  val onEnableHideBalanceChanged: (Boolean) -> Unit,
  val onBitcoinDisplayPreferenceClick: () -> Unit,
  val onBitcoinPriceCardPreferenceClick: (Boolean) -> Unit = {},
  val toolbar: ToolbarModel = ToolbarModel(
    leadingAccessory = BackAccessory(onClick = onBack)
  ),
  val header: FormHeaderModel = FormHeaderModel(
    headline = "Appearance",
    subline = "Choose what you want to see on your Home screen and how currencies show up throughout the app."
  ),
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo = EventTrackerScreenInfo(
    eventTrackerScreenId = AppearanceEventTrackerScreenId.CURRENCY_PREFERENCE
  )

  @Composable
  override fun render(modifier: Modifier) {
    BackHandler(onBack = onBack)

    val scrollState = rememberScrollState()
    val collapseRangePx = with(LocalDensity.current) { APPEARANCE_TITLE_COLLAPSE_RANGE.toPx() }
    val title = header.headline ?: "Appearance"

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
          .padding(horizontal = APPEARANCE_HORIZONTAL_PADDING)
      ) {
        Spacer(modifier = Modifier.height(APPEARANCE_TOOLBAR_RESERVED_HEIGHT))

        Label(
          modifier = Modifier
            .padding(top = APPEARANCE_LARGE_TITLE_TOP_SPACING)
            .alpha(1f - collapseProgress),
          text = title,
          type = LabelType.Display3
        )

        header.sublineModel?.let { sublineModel ->
          Label(
            modifier = Modifier.padding(top = 8.dp),
            model = sublineModel,
            type = LabelType.Body2Regular,
            treatment = LabelTreatment.Secondary
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        moneyHomeHero.render(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        CircularTabRow(
          items = AppearanceSection.entries.map { stringResource(it.label) }.toImmutableList(),
          selectedItemIndex = selectedSection.ordinal,
          onClick = { index -> onSectionSelected(AppearanceSection.entries[index]) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
          targetState = selectedSection,
          modifier = Modifier.fillMaxWidth(),
          transitionSpec = {
            fadeIn(tween(200, delayMillis = 90))
              .togetherWith(fadeOut(tween(90)))
          }
        ) { section ->
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            appearanceSectionModels(section).forEach { listGroupModel ->
              AppearancePreferenceDesignSystemListGroup(
                model = listGroupModel,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }

      AppearancePreferenceCollapsibleToolbar(
        title = title,
        collapseProgress = collapseProgress
      )
    }
  }

  @Composable
  private fun AppearancePreferenceDesignSystemListGroup(
    model: ListGroupModel,
    modifier: Modifier = Modifier,
  ) {
    Card(
      modifier = modifier,
      backgroundColor = WalletTheme.colors.secondary,
      cornerRadius = 8.dp,
      borderWidth = 0.dp,
      paddingValues = PaddingValues(0.dp)
    ) {
      model.items.forEachIndexed { index, item ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = 64.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ListItem(model = item.designSystemAppearanceItemModel())
        }

        if (index < model.items.lastIndex) {
          Divider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = appearanceDividerColor()
          )
        }
      }
    }

    model.explainerSubtext?.let { explainerSubtext ->
      Label(
        modifier = Modifier
          .padding(horizontal = 16.dp)
          .padding(top = 8.dp),
        text = explainerSubtext,
        treatment = LabelTreatment.Secondary,
        type = LabelType.Body4Regular
      )
    }
  }

  @Composable
  private fun BoxScope.AppearancePreferenceCollapsibleToolbar(
    title: String,
    collapseProgress: Float,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(
          APPEARANCE_TOP_PADDING +
            APPEARANCE_TOOLBAR_HEIGHT +
            APPEARANCE_TOOLBAR_BOTTOM_PADDING +
            APPEARANCE_TOOLBAR_BOTTOM_GRADIENT_HEIGHT
        )
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(APPEARANCE_TOP_PADDING + APPEARANCE_TOOLBAR_HEIGHT + APPEARANCE_TOOLBAR_BOTTOM_PADDING)
          .background(WalletTheme.colors.background)
      ) {
        Box(
          modifier = Modifier
            .padding(
              top = APPEARANCE_TOP_PADDING,
              start = APPEARANCE_HORIZONTAL_PADDING,
              end = APPEARANCE_HORIZONTAL_PADDING
            )
            .fillMaxWidth()
            .height(APPEARANCE_TOOLBAR_HEIGHT)
        ) {
          Toolbar(
            model = ToolbarModel(
              leadingAccessory = toolbar.leadingAccessory,
              middleAccessory = null,
              trailingAccessory = toolbar.trailingAccessory
            ),
            showDesignSystemChrome = false
          )

          Label(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                start = if (toolbar.leadingAccessory != null) APPEARANCE_INLINE_TITLE_START_PADDING else 0.dp,
                end = APPEARANCE_INLINE_TITLE_END_PADDING
              )
              .align(Alignment.CenterStart)
              .alpha(collapseProgress),
            text = title,
            type = LabelType.Title2
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(APPEARANCE_TOOLBAR_BOTTOM_GRADIENT_HEIGHT)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                WalletTheme.colors.background,
                WalletTheme.colors.background.copy(alpha = 0.65f),
                Color.Transparent
              )
            )
          )
      )
    }
  }

  private fun appearanceSectionModels(section: AppearanceSection): List<ListGroupModel> {
    return when (section) {
      AppearanceSection.DISPLAY -> listOf(
        displaySectionContent(
          themePreferenceString = themePreferenceString,
          onThemePreferenceClick = onThemePreferenceClick
        ).listGroupModel
      )
      AppearanceSection.CURRENCY ->
        currencySectionContent(
          fiatCurrencyPreferenceString = fiatCurrencyPreferenceString,
          onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
          bitcoinDisplayPreferenceString = bitcoinDisplayPreferenceString,
          onBitcoinDisplayPreferenceClick = onBitcoinDisplayPreferenceClick,
          isBitcoinPriceCardEnabled = isBitcoinPriceCardEnabled,
          onBitcoinPriceCardPreferenceClick = onBitcoinPriceCardPreferenceClick,
          defaultTimeScalePreferenceString = defaultTimeScalePreferenceString,
          onDefaultTimeScalePreferenceClick = onDefaultTimeScalePreferenceClick
        ).map(FormMainContentModel.ListGroup::listGroupModel)

      AppearanceSection.PRIVACY -> listOf(
        privacySectionContent(
          isHideBalanceEnabled = isHideBalanceEnabled,
          onEnableHideBalanceChanged = onEnableHideBalanceChanged
        ).listGroupModel
      )
    }
  }
}

@Composable
private fun appearanceDividerColor(): Color {
  return if (LocalTheme.current == Theme.DARK) {
    WalletTheme.colors.foreground30
  } else {
    WalletTheme.colors.foreground10
  }
}

private fun ListItemModel.designSystemAppearanceItemModel(): ListItemModel {
  val adjustedTrailingAccessory = when (val accessory = trailingAccessory) {
    is ListItemAccessory.IconAccessory ->
      accessory.copy(
        opticalOffsetX = accessory.opticalOffsetX ?: 4,
        model = accessory.model.copy(iconSize = IconSize.Accessory)
      )
    else -> accessory
  }

  return copy(
    titleType = LabelType.Body2Regular,
    trailingAccessory = adjustedTrailingAccessory
  )
}

private fun displaySectionContent(
  themePreferenceString: String,
  onThemePreferenceClick: () -> Unit,
): FormMainContentModel.ListGroup {
  return FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      items = immutableListOf(
        ListItemModel(
          title = "Theme",
          sideText = themePreferenceString,
          sideTextTint = ListItemSideTextTint.SECONDARY,
          trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
          onClick = onThemePreferenceClick
        )
      ),
      style = ListGroupStyle.CARD_GROUP_DIVIDER
    )
  )
}

private fun currencySectionContent(
  fiatCurrencyPreferenceString: String,
  onFiatCurrencyPreferenceClick: () -> Unit,
  bitcoinDisplayPreferenceString: String,
  onBitcoinDisplayPreferenceClick: () -> Unit,
  isBitcoinPriceCardEnabled: Boolean,
  onBitcoinPriceCardPreferenceClick: (Boolean) -> Unit,
  defaultTimeScalePreferenceString: String,
  onDefaultTimeScalePreferenceClick: () -> Unit,
): List<FormMainContentModel.ListGroup> {
  return listOf(
    FormMainContentModel.ListGroup(
      listGroupModel = ListGroupModel(
        items = immutableListOf(
          ListItemModel(
            title = "Fiat",
            sideText = fiatCurrencyPreferenceString,
            sideTextTint = ListItemSideTextTint.SECONDARY,
            trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
            onClick = onFiatCurrencyPreferenceClick
          ),
          ListItemModel(
            title = "Bitcoin",
            sideText = bitcoinDisplayPreferenceString,
            sideTextTint = ListItemSideTextTint.SECONDARY,
            trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
            onClick = onBitcoinDisplayPreferenceClick
          )
        ),
        style = ListGroupStyle.CARD_GROUP_DIVIDER
      )
    ),
    FormMainContentModel.ListGroup(
      listGroupModel = ListGroupModel(
        style = ListGroupStyle.CARD_GROUP_DIVIDER,
        items = immutableListOf(
          ListItemModel(
            title = "Show price graph",
            trailingAccessory = ListItemAccessory.SwitchAccessory(
              model = SwitchModel(
                checked = isBitcoinPriceCardEnabled,
                onCheckedChange = onBitcoinPriceCardPreferenceClick,
                testTag = "appearance-currency-show-price-graph-toggle"
              )
            )
          ),
          ListItemModel(
            title = "Default time scale",
            sideText = defaultTimeScalePreferenceString,
            sideTextTint = ListItemSideTextTint.SECONDARY,
            trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
            onClick = onDefaultTimeScalePreferenceClick
          )
        )
      )
    )
  )
}

private fun privacySectionContent(
  isHideBalanceEnabled: Boolean,
  onEnableHideBalanceChanged: (Boolean) -> Unit,
): FormMainContentModel.ListGroup {
  return FormMainContentModel.ListGroup(
    listGroupModel = ListGroupModel(
      items = immutableListOf(
        ListItemModel(
          title = "Hide balance on home screen",
          trailingAccessory = ListItemAccessory.SwitchAccessory(
            model = SwitchModel(
              checked = isHideBalanceEnabled,
              onCheckedChange = onEnableHideBalanceChanged,
              testTag = "appearance-privacy-hide-balance-toggle"
            )
          )
        )
      ),
      style = ListGroupStyle.CARD_GROUP_DIVIDER,
      explainerSubtext = "You can always tap to hide or view your balance."
    )
  )
}

@Snapshot
val SnapshotHost.appearancePreferenceCurrencySelectedPriceGraphOn
  get() = appearancePreferenceSnapshotModel(
    selectedSection = AppearanceSection.CURRENCY,
    isBitcoinPriceCardEnabled = true
  )

@Snapshot
val SnapshotHost.appearancePreferencePrivacySelectedHideBalanceOn
  get() = appearancePreferenceSnapshotModel(
    selectedSection = AppearanceSection.PRIVACY,
    isHideBalanceEnabled = true
  )

private fun appearancePreferenceSnapshotModel(
  selectedSection: AppearanceSection,
  isBitcoinPriceCardEnabled: Boolean = false,
  isHideBalanceEnabled: Boolean = false,
) = AppearancePreferenceBodyModel(
  onBack = {},
  moneyHomeHero = MoneyHomeHeroModel(
    "$0", "0 sats",
    isHidden = isHideBalanceEnabled,
    isPriceGraphEnabled = isBitcoinPriceCardEnabled,
    selectedSection = selectedSection
  ),
  selectedSection = selectedSection,
  onSectionSelected = {},
  themePreferenceString = "System",
  onThemePreferenceClick = {},
  fiatCurrencyPreferenceString = "USD",
  onFiatCurrencyPreferenceClick = {},
  bitcoinDisplayPreferenceString = "sats",
  isBitcoinPriceCardEnabled = isBitcoinPriceCardEnabled,
  defaultTimeScalePreferenceString = "1D",
  onDefaultTimeScalePreferenceClick = {},
  isHideBalanceEnabled = isHideBalanceEnabled,
  onEnableHideBalanceChanged = {},
  onBitcoinDisplayPreferenceClick = {},
  onBitcoinPriceCardPreferenceClick = {}
)

private val APPEARANCE_TOP_PADDING: Dp = 8.dp
private val APPEARANCE_HORIZONTAL_PADDING: Dp = 20.dp
private val APPEARANCE_TOOLBAR_HEIGHT: Dp = 48.dp
private val APPEARANCE_TOOLBAR_BOTTOM_PADDING: Dp = 8.dp
private val APPEARANCE_TOOLBAR_BOTTOM_GRADIENT_HEIGHT: Dp = 20.dp
private val APPEARANCE_TOOLBAR_RESERVED_HEIGHT =
  APPEARANCE_TOP_PADDING +
    APPEARANCE_TOOLBAR_HEIGHT +
    APPEARANCE_TOOLBAR_BOTTOM_PADDING +
    APPEARANCE_TOOLBAR_BOTTOM_GRADIENT_HEIGHT
private val APPEARANCE_LARGE_TITLE_TOP_SPACING: Dp = 24.dp
private val APPEARANCE_INLINE_TITLE_START_PADDING: Dp = 56.dp
private val APPEARANCE_INLINE_TITLE_END_PADDING: Dp = 56.dp
private val APPEARANCE_TITLE_COLLAPSE_RANGE: Dp = 120.dp

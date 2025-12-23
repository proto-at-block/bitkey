package build.wallet.statemachine.money.currency

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.AppearanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.app.core.form.MoneyHomeHero
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

class AppearancePreferenceBodyModel(
  override val onBack: () -> Unit,
  val moneyHomeHero: FormMainContentModel.MoneyHomeHero,
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
    Column(
      modifier = modifier
        .background(WalletTheme.colors.background)
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Toolbar(
        modifier = Modifier.fillMaxWidth(),
        model = toolbar
      )

      Header(
        model = header,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(16.dp))

      MoneyHomeHero(
        model = moneyHomeHero,
        selectedSection = selectedSection,
        isDarkMode = LocalTheme.current == Theme.DARK,
        isPriceGraphEnabled = isBitcoinPriceCardEnabled
      )

      Spacer(modifier = Modifier.height(16.dp))

      CircularTabRow(
        items = AppearanceSection.entries.map { stringResource(it.label) }.toImmutableList(),
        selectedItemIndex = selectedSection.ordinal,
        onClick = { index -> onSectionSelected(AppearanceSection.entries[index]) },
        backgroundColor = WalletTheme.colors.subtleBackground
      )

      Spacer(modifier = Modifier.height(16.dp))

      AnimatedContent(
        targetState = selectedSection,
        modifier = Modifier.weight(1f),
        transitionSpec = {
          fadeIn(tween(200, delayMillis = 90))
            .togetherWith(fadeOut(tween(90)))
        }
      ) { section ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          when (section) {
            AppearanceSection.DISPLAY -> {
              val displayContent = displaySectionContent(
                themePreferenceString = themePreferenceString,
                onThemePreferenceClick = onThemePreferenceClick
              )
              ListGroup(
                model = displayContent.listGroupModel,
                modifier = Modifier.fillMaxWidth()
              )
            }
            AppearanceSection.CURRENCY -> {
              val currencyContent = currencySectionContent(
                fiatCurrencyPreferenceString = fiatCurrencyPreferenceString,
                onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
                bitcoinDisplayPreferenceString = bitcoinDisplayPreferenceString,
                onBitcoinDisplayPreferenceClick = onBitcoinDisplayPreferenceClick,
                isBitcoinPriceCardEnabled = isBitcoinPriceCardEnabled,
                onBitcoinPriceCardPreferenceClick = onBitcoinPriceCardPreferenceClick,
                defaultTimeScalePreferenceString = defaultTimeScalePreferenceString,
                onDefaultTimeScalePreferenceClick = onDefaultTimeScalePreferenceClick
              )
              currencyContent.forEach { listGroup ->
                ListGroup(
                  model = listGroup.listGroupModel,
                  modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
              }
            }
            AppearanceSection.PRIVACY -> {
              val privacyContent = privacySectionContent(
                isHideBalanceEnabled = isHideBalanceEnabled,
                onEnableHideBalanceChanged = onEnableHideBalanceChanged
              )
              ListGroup(
                model = privacyContent.listGroupModel,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }
      }
    }
  }
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
                onCheckedChange = onBitcoinPriceCardPreferenceClick
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
              onCheckedChange = onEnableHideBalanceChanged
            )
          )
        )
      ),
      style = ListGroupStyle.CARD_GROUP_DIVIDER,
      explainerSubtext = "You can always tap to hide or view your balance."
    )
  )
}

package build.wallet.statemachine.money.currency

import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun CurrencyPreferenceFormModel(
  onBack: () -> Unit,
  moneyHomeHero: FormMainContentModel.MoneyHomeHero,
  fiatCurrencyPreferenceString: String,
  onFiatCurrencyPreferenceClick: () -> Unit,
  bitcoinDisplayPreferenceString: String,
  bitcoinDisplayPreferencePickerModel: ListItemPickerMenu<*>,
  shouldShowHideBalance: Boolean = false,
  isHideBalanceEnabled: Boolean = false,
  onEnableHideBalanceChanged: (Boolean) -> Unit,
  onBitcoinDisplayPreferenceClick: () -> Unit,
) = FormBodyModel(
  id = CurrencyEventTrackerScreenId.CURRENCY_PREFERENCE,
  onBack = onBack,
  toolbar = ToolbarModel(
    leadingAccessory = BackAccessory(onClick = onBack)
  ),
  header =
    FormHeaderModel(
      headline = "Currency Display",
      subline = "Choose how you want currencies to display throughout the app."
    ),
  mainContentList =
    buildImmutableList {
      moneyHomeHero.apply { add(this) }
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            header = "Currency",
            items =
              immutableListOf(
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
                  onClick = onBitcoinDisplayPreferenceClick,
                  pickerMenu = bitcoinDisplayPreferencePickerModel
                )
              ),
            style = ListGroupStyle.CARD_GROUP_DIVIDER
          )
      ).apply { add(this) }
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            header = "Privacy",
            items =
              immutableListOf(
                ListItemModel(
                  title = "Hide Home Balance by default",
                  trailingAccessory = ListItemAccessory.SwitchAccessory(
                    model = SwitchModel(
                      checked = isHideBalanceEnabled,
                      onCheckedChange = onEnableHideBalanceChanged
                    )
                  )
                )
              ),
            style = ListGroupStyle.CARD_GROUP_DIVIDER
          )
      ).apply {
        if (shouldShowHideBalance) {
          add(this)
        }
      }
    },
  primaryButton = null
)

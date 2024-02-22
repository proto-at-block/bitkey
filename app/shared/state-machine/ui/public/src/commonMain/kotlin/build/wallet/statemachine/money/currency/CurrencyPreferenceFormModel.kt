package build.wallet.statemachine.money.currency

import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun CurrencyPreferenceFormModel(
  onBack: (() -> Unit)?,
  moneyHomeHero: FormMainContentModel.MoneyHomeHero,
  fiatCurrencyPreferenceString: String,
  onFiatCurrencyPreferenceClick: () -> Unit,
  bitcoinDisplayPreferenceString: String,
  bitcoinDisplayPreferencePickerModel: ListItemPickerMenu<*>,
  onBitcoinDisplayPreferenceClick: () -> Unit,
  onDone: (() -> Unit)?,
) = FormBodyModel(
  id = CurrencyEventTrackerScreenId.CURRENCY_PREFERENCE,
  onBack = onBack,
  toolbar =
    onBack?.let {
      ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onBack)
      )
    },
  header =
    FormHeaderModel(
      headline = "Currency",
      subline = "Choose how you want currencies to display throughout the app."
    ),
  mainContentList =
    immutableListOf(
      moneyHomeHero,
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
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
      )
    ),
  primaryButton =
    onDone?.let {
      ButtonModel(
        text = "Done",
        size = ButtonModel.Size.Footer,
        onClick = Click.standardClick { onDone() }
      )
    }
)

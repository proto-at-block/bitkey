package build.wallet.statemachine.money.currency

import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class AppearancePreferenceFormModel(
  override val onBack: () -> Unit,
  val moneyHomeHero: FormMainContentModel.MoneyHomeHero,
  val fiatCurrencyPreferenceString: String,
  val onFiatCurrencyPreferenceClick: () -> Unit,
  val bitcoinDisplayPreferenceString: String,
  val bitcoinDisplayPreferencePickerModel: ListItemPickerMenu<*>,
  val shouldShowBitcoinPriceCardToggle: Boolean = false,
  val isBitcoinPriceCardEnabled: Boolean = false,
  val isHideBalanceEnabled: Boolean = false,
  val onEnableHideBalanceChanged: (Boolean) -> Unit,
  val onBitcoinDisplayPreferenceClick: () -> Unit,
  val onBitcoinPriceCardPreferenceClick: (Boolean) -> Unit = {},
) : FormBodyModel(
    id = CurrencyEventTrackerScreenId.CURRENCY_PREFERENCE,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    header = FormHeaderModel(
      headline = "Appearance",
      subline = "Choose what you want to see on your Home screen and how currencies show up throughout the app."
    ),
    mainContentList = buildImmutableList {
      moneyHomeHero.apply { add(this) }
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          header = "Currency",
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
              onClick = onBitcoinDisplayPreferenceClick,
              pickerMenu = bitcoinDisplayPreferencePickerModel
            )
          ),
          style = ListGroupStyle.CARD_GROUP_DIVIDER
        )
      ).apply { add(this) }

      if (shouldShowBitcoinPriceCardToggle) {
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            style = ListGroupStyle.CARD_GROUP_DIVIDER,
            items = immutableListOf(
              ListItemModel(
                title = "Show Bitcoin Performance",
                trailingAccessory = ListItemAccessory.SwitchAccessory(
                  model = SwitchModel(
                    checked = isBitcoinPriceCardEnabled,
                    onCheckedChange = onBitcoinPriceCardPreferenceClick
                  )
                )
              )
            )
          )
        ).apply { add(this) }
      }
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          header = "Privacy",
          items = immutableListOf(
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
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          explainerSubtext = "You can always tap your Home Balance to quickly hide or reveal your holdings."
        )
      ).apply {
        add(this)
      }
    },
    primaryButton = null
  )
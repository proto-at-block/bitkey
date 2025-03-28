package build.wallet.statemachine.money.currency

import build.wallet.analytics.events.screen.id.AppearanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

data class FiatCurrencyListFormModel(
  val onClose: () -> Unit,
  val selectedCurrency: FiatCurrency,
  val currencyList: List<FiatCurrency>,
  val onCurrencySelection: (FiatCurrency) -> Unit,
) : FormBodyModel(
    id = AppearanceEventTrackerScreenId.CURRENCY_FIAT_LIST_SELECTION,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onClose),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Fiat")
    ),
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = currencyList
            .map { currency ->
              currency.displayConfiguration.let {
                ListItemModel(
                  title = currency.textCode.code,
                  secondaryText = it.name,
                  leadingAccessory = ListItemAccessory.TextAccessory(it.flagEmoji),
                  trailingAccessory =
                    if (selectedCurrency == currency) {
                      ListItemAccessory.IconAccessory(
                        model =
                          IconModel(
                            icon = Icon.SmallIconCheckFilled,
                            iconSize = IconSize.Small,
                            iconTint = IconTint.Primary
                          )
                      )
                    } else {
                      null
                    },
                  onClick = { onCurrencySelection(currency) }
                )
              }
            }.toImmutableList(),
          style = ListGroupStyle.NONE
        )
      )
    ),
    primaryButton = null
  )

package build.wallet.statemachine.moneyhome.lite

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.moneyhome.BaseMoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.lite.card.BuyOwnBitkeyMoneyHomeCardModel
import build.wallet.statemachine.moneyhome.lite.card.WalletsProtectingMoneyHomeCardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import kotlinx.collections.immutable.ImmutableList

data class LiteMoneyHomeBodyModel(
  override val trailingToolbarAccessoryModel: ToolbarAccessoryModel,
  override val buttonsModel: MoneyHomeButtonsModel,
  override val cardsModel: MoneyHomeCardsModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = MoneyHomeEventTrackerScreenId.MONEY_HOME
    ),
) : BodyModel(), BaseMoneyHomeBodyModel {
  constructor(
    onSettings: () -> Unit,
    buttonModel: MoneyHomeButtonsModel,
    protectedCustomers: ImmutableList<ProtectedCustomer>,
    badgedSettingsIcon: Boolean,
    onProtectedCustomerClick: (ProtectedCustomer) -> Unit,
    onBuyOwnBitkeyClick: () -> Unit,
    onAcceptInviteClick: () -> Unit,
  ) : this(
    cardsModel = MoneyHomeCardsModel(
      cards = immutableListOf(
        // Wallets you're Protecting card
        WalletsProtectingMoneyHomeCardModel(
          protectedCustomers = protectedCustomers,
          onProtectedCustomerClick = onProtectedCustomerClick,
          onAcceptInviteClick = onAcceptInviteClick,
          isLiteMode = true
        ),
        // Buy your Own Bitkey card
        BuyOwnBitkeyMoneyHomeCardModel(onClick = onBuyOwnBitkeyClick)
      )
    ),
    buttonsModel = buttonModel,
    trailingToolbarAccessoryModel =
      ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = if (badgedSettingsIcon) {
              Icon.SmallIconSettingsBadged
            } else {
              Icon.SmallIconSettings
            },
            iconSize = IconSize.HeaderToolbar
          ),
          onClick = StandardClick(onSettings)
        )
      )
  )
}

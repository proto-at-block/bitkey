package build.wallet.statemachine.moneyhome

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * @property balanceModel - model for rendering latest balance.
 * @property buttonsModel - A list of model that describe the buttons that will be used to move money
 * These buttons will be in the form of an icon.
 * @property seeAllButtonModel - model for "See All" button under transactions model. Null if should be hidden.
 * @property refresh - called by UI on iOS when the transactions pull to refresh is engaged, performs
 * the actual refresh in async manner.
 * @property onRefresh - called by UI on Android when the transactions pull to refresh is engaged,
 * just indicates that refresh has started, doesn't block the caller
 * @param isRefreshing - used in conjunction with [onRefreshSync] on Android to tell the UI
 * when the work is done.
 *
 * Note: the different refresh APIs here are due to the platform differences for refresh controls
 * on iOS and Android.
 */
data class MoneyHomeBodyModel(
  override val trailingToolbarAccessoryModel: ToolbarAccessoryModel,
  val hideBalance: Boolean,
  @Redacted
  val balanceModel: MoneyAmountModel,
  override val buttonsModel: MoneyHomeButtonsModel,
  override val cardsModel: MoneyHomeCardsModel,
  @Redacted
  val transactionsModel: ListModel?,
  val seeAllButtonModel: ButtonModel?,
  val refresh: suspend () -> Unit,
  val onRefresh: () -> Unit,
  val onHideBalance: () -> Unit,
  val isRefreshing: Boolean,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = MoneyHomeEventTrackerScreenId.MONEY_HOME
    ),
) : BodyModel(), BaseMoneyHomeBodyModel {
  constructor(
    hideBalance: Boolean,
    onSettings: () -> Unit,
    balanceModel: MoneyAmountModel,
    buttonsModel: MoneyHomeButtonsModel,
    cardsModel: MoneyHomeCardsModel,
    transactionsModel: ListModel?,
    seeAllButtonModel: ButtonModel?,
    refresh: suspend () -> Unit,
    onRefresh: () -> Unit,
    onHideBalance: () -> Unit,
    isRefreshing: Boolean,
  ) : this(
    hideBalance = hideBalance,
    onHideBalance = onHideBalance,
    trailingToolbarAccessoryModel =
      ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconSettings,
            iconSize = IconSize.HeaderToolbar
          ),
          onClick = StandardClick(onSettings)
        )
      ),
    balanceModel = balanceModel,
    buttonsModel = buttonsModel,
    cardsModel = cardsModel,
    transactionsModel = transactionsModel,
    seeAllButtonModel = seeAllButtonModel,
    refresh = refresh,
    onRefresh = onRefresh,
    isRefreshing = isRefreshing
  )
}

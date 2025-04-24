package build.wallet.statemachine.moneyhome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.ui.app.moneyhome.MoneyHomeScreen
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import dev.zacsweers.redacted.annotations.Redacted

/**
 * @property balanceModel - model for rendering latest balance.
 * @property buttonsModel - A list of model that describe the buttons that will be used to move money
 * These buttons will be in the form of an icon.
 * @property seeAllButtonModel - model for "See All" button under transactions model. Null if should be hidden.
 * the actual refresh in async manner.
 * @property onRefresh - called by UI on Android when the transactions pull to refresh is engaged,
 * just indicates that refresh has started, doesn't block the caller
 * @param isRefreshing - used in conjunction with [onRefresh] on Android to tell the UI
 * when the work is done.
 */
data class MoneyHomeBodyModel(
  override val trailingToolbarAccessoryModel: ToolbarAccessoryModel,
  val onSettings: () -> Unit,
  val hideBalance: Boolean,
  @Redacted
  val balanceModel: MoneyAmountModel,
  override val buttonsModel: MoneyHomeButtonsModel,
  override val cardsModel: CardListModel,
  @Redacted
  val transactionsModel: ListModel?,
  val seeAllButtonModel: ButtonModel?,
  val coachmark: CoachmarkModel?,
  val onRefresh: () -> Unit,
  val onHideBalance: () -> Unit,
  val onOpenPriceDetails: () -> Unit,
  val isRefreshing: Boolean,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = MoneyHomeEventTrackerScreenId.MONEY_HOME
  ),
  val onSecurityHubTabClick: (() -> Unit)? = null,
  val isSecurityHubBadged: Boolean = false,
) : BodyModel(), BaseMoneyHomeBodyModel {
  @Composable
  override fun render(modifier: Modifier) {
    MoneyHomeScreen(modifier, model = this)
  }
}

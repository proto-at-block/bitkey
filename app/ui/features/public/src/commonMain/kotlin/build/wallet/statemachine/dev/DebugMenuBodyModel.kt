package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.DEBUG_MENU
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.ui.app.dev.DebugMenuScreen
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.list.ListGroupModel
import kotlinx.collections.immutable.ImmutableList

data class DebugMenuBodyModel(
  val title: String,
  override val onBack: () -> Unit,
  val groups: ImmutableList<ListGroupModel>,
  val alertModel: ButtonAlertModel? = null,
  val bottomSheetModel: SheetModel? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(DEBUG_MENU),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    DebugMenuScreen(modifier, model = this)
  }
}

package build.wallet.statemachine.dev

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.list.ListGroupModel
import kotlinx.collections.immutable.ImmutableList

data class DebugMenuBodyModel(
  val title: String,
  override val onBack: () -> Unit,
  val groups: ImmutableList<ListGroupModel>,
  val alertModel: ButtonAlertModel? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = GeneralEventTrackerScreenId.DEBUG_MENU
    ),
) : BodyModel()

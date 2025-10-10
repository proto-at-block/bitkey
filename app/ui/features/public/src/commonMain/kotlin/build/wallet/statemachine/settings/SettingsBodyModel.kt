package build.wallet.statemachine.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.ui.app.settings.SettingsScreen
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

data class SettingsBodyModel(
  override val onBack: () -> Unit,
  val toolbarModel: ToolbarModel,
  val sectionModels: ImmutableList<SectionModel>,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = SettingsEventTrackerScreenId.SETTINGS
    ),
  val onSecurityHubCoachmarkClick: (() -> Unit)?,
) : BodyModel() {
  data class SectionModel(
    val sectionHeaderTitle: String,
    val rowModels: ImmutableList<RowModel>,
  )

  data class RowModel(
    val icon: Icon,
    val title: String,
    val isDisabled: Boolean,
    val coachmarkLabelModel: CoachmarkLabelModel? = null,
    val onClick: () -> Unit,
  )

  @Composable
  override fun render(modifier: Modifier) {
    SettingsScreen(modifier, model = this)
  }
}

package build.wallet.statemachine.dev.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.dev.analytics.AnalyticsScreen
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Model for showing screen with events and various debugging options.
 *
 * @property onClear - if called, in-memory events will be wiped.
 */
data class AnalyticsBodyModel(
  val isEnabled: Boolean,
  val onEnableChanged: (Boolean) -> Unit,
  val onClear: () -> Unit,
  val events: ImmutableList<ListItemModel>,
  override val onBack: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    AnalyticsScreen(modifier, model = this)
  }
}

package build.wallet.ui.model.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.ComposeBodyModel
import build.wallet.ui.components.tab.CircularTabRow

@Immutable
data class CircularTabRowBodyModel(
  val items: List<String>,
  val selectedItemIndex: Int = 0,
  val onClick: (index: Int) -> Unit = {},
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : ComposeBodyModel() {
  @Composable
  override fun render() {
    CircularTabRow(model = this)
  }
}

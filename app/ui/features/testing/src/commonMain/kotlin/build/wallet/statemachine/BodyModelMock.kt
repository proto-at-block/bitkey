package build.wallet.statemachine

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

/** A fake [BodyModel] for testing, identifiable by its id. */
data class BodyModelMock<PropsT : Any>(
  val id: String,
  val latestProps: PropsT,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    Text(this.toString())
  }
}

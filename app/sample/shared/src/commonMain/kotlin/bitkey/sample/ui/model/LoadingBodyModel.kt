package bitkey.sample.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.sample.ui.core.LoadingScreen
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class LoadingBodyModel(
  val message: String,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    LoadingScreen(model = this)
  }
}

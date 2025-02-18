package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.platform.random.uuid
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.ui.app.core.LoadingSuccessScreen

/**
 * Model for a screen that seamlessly transitions from loading to success
 *
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 */
data class LoadingSuccessBodyModel(
  override val onBack: (() -> Unit)? = null,
  val state: State,
  val id: EventTrackerScreenId?,
  val message: String? = null,
  val eventTrackerContext: EventTrackerContext? = null,
  val eventTrackerShouldTrack: Boolean = true,
) : BodyModel(), AutomaticUiTests {
  sealed interface State {
    data object Loading : State

    data object Success : State
  }

  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() =
      id?.let {
        EventTrackerScreenInfo(
          eventTrackerScreenId = it,
          eventTrackerContext = eventTrackerContext,
          eventTrackerShouldTrack = eventTrackerShouldTrack
        )
      }

  private val unique = id?.name ?: uuid()
  override val key: String = "${this::class.qualifiedName}-$unique."

  override fun automateNextPrimaryScreen() {
    // No-op necessary: Loading screens better advance on their own.
  }

  @Composable
  override fun render(modifier: Modifier) {
    LoadingSuccessScreen(modifier, model = this)
  }
}

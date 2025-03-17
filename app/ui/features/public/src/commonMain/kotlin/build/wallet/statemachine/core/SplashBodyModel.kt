package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.ui.app.loading.SplashScreen
import kotlin.time.Duration
import kotlin.time.DurationUnit

data class SplashBodyModel(
  /** The duration of delay for the animation of the Bitkey word mark to appear */
  val bitkeyWordMarkAnimationDelay: Duration,
  /** The duration of the animation of the Bitkey word mark to appear */
  val bitkeyWordMarkAnimationDuration: Duration,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = GeneralEventTrackerScreenId.SPLASH_SCREEN
    ),
) : BodyModel() {
  // For iOS-interop
  val bitkeyWordMarkAnimationDelayInSeconds =
    bitkeyWordMarkAnimationDelay
      .toDouble(unit = DurationUnit.SECONDS)

  // For iOS-interop
  val bitkeyWordMarkAnimationDurationInSeconds =
    bitkeyWordMarkAnimationDuration
      .toDouble(unit = DurationUnit.SECONDS)

  @Composable
  override fun render(modifier: Modifier) {
    SplashScreen(modifier, model = this)
  }
}

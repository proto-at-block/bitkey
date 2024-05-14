package bitkey.sample.ui.onboarding

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class RequestAccountNameBodyModel(
  val onEnterAccountName: (String) -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

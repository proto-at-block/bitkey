package bitkey.sample.ui.onboarding

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class AccountCreatedBodyModel(
  val accountName: String,
  val onContinue: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

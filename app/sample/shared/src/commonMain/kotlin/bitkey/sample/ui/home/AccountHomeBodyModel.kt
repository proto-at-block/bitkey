package bitkey.sample.ui.home

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class AccountHomeBodyModel(
  val accountId: String,
  val accountName: String,
  val onSettingsClick: () -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

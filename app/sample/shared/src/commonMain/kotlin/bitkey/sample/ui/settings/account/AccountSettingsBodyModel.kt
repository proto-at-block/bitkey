package bitkey.sample.ui.settings.account

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class AccountSettingsBodyModel(
  val deletingAccount: Boolean,
  val onDeleteAccountClick: () -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null
}

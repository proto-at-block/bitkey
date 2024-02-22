package build.wallet.statemachine.platform.permissions

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class AskingToGoToSystemBodyModel(
  val title: String,
  val explanation: String,
  val onGoToSetting: () -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

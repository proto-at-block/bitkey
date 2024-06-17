package build.wallet.statemachine.settings.full.device.resetdevice.complete

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class ResettingDeviceSuccessBodyModel(
  val onDone: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

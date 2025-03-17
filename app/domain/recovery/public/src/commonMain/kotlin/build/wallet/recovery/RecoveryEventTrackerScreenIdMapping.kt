package build.wallet.recovery

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware

fun PhysicalFactor.getEventId(
  app: DelayNotifyRecoveryEventTrackerScreenId,
  hw: HardwareRecoveryEventTrackerScreenId,
): EventTrackerScreenId =
  when (this) {
    App -> app
    Hardware -> hw
  }

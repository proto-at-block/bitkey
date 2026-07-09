package build.wallet.statemachine.settings

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

object SettingsAppSegment : AppSegment {
  override val id: String = "Settings"

  object Device : AppSegment by SettingsAppSegment.childSegment("Device")

  object Electrum : AppSegment by SettingsAppSegment.childSegment("Electrum")

  object Feedback : AppSegment by SettingsAppSegment.childSegment("Feedback")
}

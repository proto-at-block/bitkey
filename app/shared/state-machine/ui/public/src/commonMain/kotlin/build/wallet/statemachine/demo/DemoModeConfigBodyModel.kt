package build.wallet.statemachine.demo

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.model.switch.SwitchModel

enum class DemoCodeTrackerScreenId : EventTrackerScreenId {
  /** Screen to enter a code for demo mode */
  DEMO_CODE_CONFIG,
  DEMO_MODE_CODE_ENTRY,
  DEMO_MODE_CODE_SUBMISSION,
}

data class DemoModeConfigBodyModel(
  override val onBack: () -> Unit,
  val switchCardModel: SwitchCardModel,
  val disableAlertModel: AlertModel?,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = DemoCodeTrackerScreenId.DEMO_CODE_CONFIG
    ),
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    switchIsChecked: Boolean,
    onSwitchCheckedChange: (Boolean) -> Unit,
    disableAlertModel: AlertModel?,
  ) : this(
    onBack = onBack,
    switchCardModel =
      SwitchCardModel(
        title = "Enable demo mode",
        subline = "Demo mode enables you to test the app without having a physical hardware device.",
        switchModel =
          SwitchModel(
            checked = switchIsChecked,
            onCheckedChange = onSwitchCheckedChange
          ),
        actionRows = emptyImmutableList()
      ),
    disableAlertModel = disableAlertModel
  )
}

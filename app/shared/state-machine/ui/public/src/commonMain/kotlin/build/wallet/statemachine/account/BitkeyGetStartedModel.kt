package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel

data class BitkeyGetStartedModel(
  val onLogoClick: () -> Unit,
  val onGetStartedClick: () -> Unit,
) : BodyModel() {
  val getStartedButtonModel =
    ButtonModel(
      text = "Get Started",
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick { onGetStartedClick() }
    )
  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = GeneralEventTrackerScreenId.BITKEY_GET_STARTED
    )
}

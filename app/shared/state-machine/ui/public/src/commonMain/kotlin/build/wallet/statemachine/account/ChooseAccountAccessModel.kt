package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

data class ChooseAccountAccessModel(
  val title: String,
  val subtitle: String,
  val buttons: List<ButtonModel>,
  val onLogoClick: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS
    ),
) : BodyModel() {
  constructor(
    onLogoClick: () -> Unit,
    onSetUpNewWalletClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
  ) : this(
    onLogoClick = onLogoClick,
    title = "Own your bitcoin.",
    subtitle = "Bitkey is the safest way to own, store, and spend your bitcoin.",
    buttons =
      buildList {
        add(
          ButtonModel(
            text = "Set up a new wallet",
            size = Footer,
            treatment = ButtonModel.Treatment.White,
            onClick = Click.standardClick { onSetUpNewWalletClick() },
            testTag = "setup-new-wallet"
          )
        )

        add(
          ButtonModel(
            text = "More options",
            size = Footer,
            treatment = ButtonModel.Treatment.Translucent10,
            onClick = Click.standardClick { onMoreOptionsClick() },
            testTag = "more-options"
          )
        )
      }
  )
}

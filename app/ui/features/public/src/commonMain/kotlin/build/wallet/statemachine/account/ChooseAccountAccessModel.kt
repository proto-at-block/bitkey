package build.wallet.statemachine.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.app.account.ChooseAccountAccessScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

data class ChooseAccountAccessModel(
  val title: String,
  val subtitle: String,
  val buttons: List<ButtonModel>,
  val onLogoClick: () -> Unit,
  val legalNotice: LabelModel.LinkSubstringModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS
    ),
) : BodyModel() {
  constructor(
    onLogoClick: () -> Unit,
    onSetUpNewWalletClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit = {},
    onPrivacyNoticeClick: () -> Unit = {},
  ) : this(
    onLogoClick = onLogoClick,
    title = "Own your bitcoin",
    subtitle = "Bitcoin ownership that's easy to use and hard to lose.",
    buttons =
      buildImmutableList {
        add(
          ButtonModel(
            text = "Set up a new wallet",
            size = Footer,
            treatment = ButtonModel.Treatment.White,
            onClick = StandardClick(onSetUpNewWalletClick)
          )
        )

        add(
          ButtonModel(
            text = "More options",
            size = Footer,
            treatment = ButtonModel.Treatment.Translucent10,
            onClick = StandardClick(onMoreOptionsClick)
          )
        )
      },
    legalNotice = buildChooseAccountAccessLegalNotice(
      onTermsOfServiceClick = onTermsOfServiceClick,
      onPrivacyNoticeClick = onPrivacyNoticeClick
    )
  )

  @Composable
  override fun render(modifier: Modifier) {
    ChooseAccountAccessScreen(modifier, model = this)
  }
}

private fun buildChooseAccountAccessLegalNotice(
  onTermsOfServiceClick: () -> Unit,
  onPrivacyNoticeClick: () -> Unit,
) = LabelModel.LinkSubstringModel.from(
  string = "By setting up a new wallet you agree to Bitkey's Terms of Service and Privacy Notice.",
  substringToOnClick = linkedMapOf(
    "Terms of Service" to onTermsOfServiceClick,
    "Privacy Notice" to onPrivacyNoticeClick
  ),
  underline = true,
  bold = false,
  color = LabelModel.Color.UNSPECIFIED
)

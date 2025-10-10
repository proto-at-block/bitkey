package build.wallet.statemachine.moneyhome.full.coachmarks

import androidx.compose.runtime.Composable
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.coachmark.CoachmarkModel

@Composable
fun SecurityHubHomeCoachmarkModel(onDismiss: () -> Unit): CoachmarkModel {
  return CoachmarkModel(
    identifier = CoachmarkIdentifier.SecurityHubHomeCoachmark,
    title = "Bitkey Security, Simplified",
    description = "The new Security Hub gives you a clear view of your setup and lets you know if anything needs your attention.",
    arrowPosition = CoachmarkModel.ArrowPosition(
      vertical = CoachmarkModel.ArrowPosition.Vertical.Bottom,
      horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
    ),
    button = null,
    image = null,
    dismiss = onDismiss
  )
}

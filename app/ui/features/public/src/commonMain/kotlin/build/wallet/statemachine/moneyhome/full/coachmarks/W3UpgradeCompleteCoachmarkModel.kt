package build.wallet.statemachine.moneyhome.full.coachmarks

import androidx.compose.runtime.Composable
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.coachmark.CoachmarkModel

@Composable
fun W3UpgradeCompleteCoachmarkModel(onDismiss: () -> Unit): CoachmarkModel {
  return CoachmarkModel(
    identifier = CoachmarkIdentifier.W3UpgradeCompleteCoachmark,
    title = "Your wallet is ready",
    description = "Start using your new Bitkey device anytime.",
    arrowPosition = CoachmarkModel.ArrowPosition(
      vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
      horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
    ),
    button = null,
    image = null,
    dismiss = onDismiss
  )
}

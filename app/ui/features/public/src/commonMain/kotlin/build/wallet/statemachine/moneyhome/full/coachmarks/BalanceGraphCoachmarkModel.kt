package build.wallet.statemachine.moneyhome.full.coachmarks

import androidx.compose.runtime.Composable
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.coachmark.CoachmarkModel

@Composable
fun BalanceGraphCoachmarkModel(onDismiss: () -> Unit): CoachmarkModel {
  return CoachmarkModel(
    identifier = CoachmarkIdentifier.BalanceGraphCoachmark,
    title = "View your performance",
    description = "Tap the price graph to see the performance of your bitcoin balance over time.",
    arrowPosition = CoachmarkModel.ArrowPosition(
      vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
      horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
    ),
    button = null,
    image = null,
    dismiss = onDismiss
  )
}

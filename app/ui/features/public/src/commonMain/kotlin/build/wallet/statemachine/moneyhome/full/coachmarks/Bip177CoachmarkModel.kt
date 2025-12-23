package build.wallet.statemachine.moneyhome.full.coachmarks

import androidx.compose.runtime.Composable
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.coachmark.CoachmarkModel

@Composable
fun Bip177CoachmarkModel(onDismiss: () -> Unit): CoachmarkModel {
  return CoachmarkModel(
    identifier = CoachmarkIdentifier.Bip177Coachmark,
    title = "Sats are now ₿!",
    description = "The amount formerly known as sats is now ₿ in your app.",
    arrowPosition = CoachmarkModel.ArrowPosition(
      vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
      horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
    ),
    button = null,
    image = null,
    dismiss = onDismiss
  )
}

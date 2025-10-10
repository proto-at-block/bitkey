package build.wallet.statemachine.moneyhome.full.coachmarks

import androidx.compose.runtime.Composable
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel

@Composable
fun PrivateWalletHomeCoachmarkModel(
  onDismiss: () -> Unit,
  onGoToPrivateWalletMigration: () -> Unit,
): CoachmarkModel {
  return CoachmarkModel(
    identifier = CoachmarkIdentifier.PrivateWalletHomeCoachmark,
    title = "Enhanced Wallet Privacy",
    description = "Upgrade your wallet to get total balance privacy.",
    arrowPosition = CoachmarkModel.ArrowPosition(
      vertical = CoachmarkModel.ArrowPosition.Vertical.Bottom,
      horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
    ),
    button = ButtonModel(
      text = "Learn more",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        onGoToPrivateWalletMigration()
      }
    ),
    image = null,
    dismiss = onDismiss
  )
}

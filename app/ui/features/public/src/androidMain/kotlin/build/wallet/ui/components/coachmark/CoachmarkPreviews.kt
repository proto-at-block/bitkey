package build.wallet.ui.components.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.moneyhome.full.coachmarks.Bip177CoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.PrivateWalletHomeCoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.W3UpgradeCompleteCoachmarkModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Private Wallet Home Coachmark")
@Composable
fun PrivateWalletHomeCoachmarkPreview() {
  PreviewWalletTheme {
    Coachmark(
      model = PrivateWalletHomeCoachmarkModel(
        onDismiss = {},
        onGoToPrivateWalletMigration = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview(name = "Private Wallet Home Coachmark DSV2")
@Composable
fun PrivateWalletHomeCoachmarkDesignSystemV2Preview() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
    Coachmark(
      model = PrivateWalletHomeCoachmarkModel(
        onDismiss = {},
        onGoToPrivateWalletMigration = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview(name = "BIP177 Coachmark")
@Composable
fun Bip177CoachmarkPreview() {
  PreviewWalletTheme {
    Coachmark(
      model = Bip177CoachmarkModel(onDismiss = {}),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview(name = "BIP177 Coachmark DSV2")
@Composable
fun Bip177CoachmarkDesignSystemV2Preview() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
    Coachmark(
      model = Bip177CoachmarkModel(onDismiss = {}),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview(name = "W3 Upgrade Complete Coachmark")
@Composable
fun W3UpgradeCompleteCoachmarkPreview() {
  PreviewWalletTheme {
    Coachmark(
      model = W3UpgradeCompleteCoachmarkModel(onDismiss = {}),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview(name = "W3 Upgrade Complete Coachmark DSV2")
@Composable
fun W3UpgradeCompleteCoachmarkDesignSystemV2Preview() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
    Coachmark(
      model = W3UpgradeCompleteCoachmarkModel(onDismiss = {}),
      offset = Offset(0f, 0f)
    )
  }
}

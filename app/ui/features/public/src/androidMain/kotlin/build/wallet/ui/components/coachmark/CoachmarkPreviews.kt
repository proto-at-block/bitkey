package build.wallet.ui.components.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.moneyhome.full.coachmarks.BalanceGraphCoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.PrivateWalletHomeCoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.SecurityHubHomeCoachmarkModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun BalanceGraphCoachmarkPreview() {
  PreviewWalletTheme {
    Coachmark(
      model = BalanceGraphCoachmarkModel(
        onDismiss = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview
@Composable
internal fun SecurityHubHomeCoachmarkPreview() {
  PreviewWalletTheme {
    Coachmark(
      model = SecurityHubHomeCoachmarkModel(
        onDismiss = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}

@Preview
@Composable
internal fun PrivateWalletHomeCoachmarkPreview() {
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

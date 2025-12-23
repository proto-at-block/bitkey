package build.wallet.ui.components.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.moneyhome.full.coachmarks.PrivateWalletHomeCoachmarkModel
import build.wallet.ui.tooling.PreviewWalletTheme

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

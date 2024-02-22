package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.Color
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.Color.BLUE
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.Color.GREEN
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.Color.ON60
import build.wallet.ui.theme.WalletTheme

@Composable
internal fun Color.toWalletTheme(): androidx.compose.ui.graphics.Color {
  return when (this) {
    GREEN -> WalletTheme.colors.deviceLEDGreen
    BLUE -> WalletTheme.colors.deviceLEDBlue
    ON60 -> WalletTheme.colors.foreground60
  }
}

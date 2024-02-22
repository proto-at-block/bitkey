package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.theme.WalletTheme

@Composable
internal fun LabelModel.StringWithStyledSubstringModel.Color.toWalletTheme(): androidx.compose.ui.graphics.Color {
  return when (this) {
    LabelModel.StringWithStyledSubstringModel.Color.GREEN -> WalletTheme.colors.deviceLEDGreen
    LabelModel.StringWithStyledSubstringModel.Color.BLUE -> WalletTheme.colors.deviceLEDBlue
    LabelModel.StringWithStyledSubstringModel.Color.ON60 -> WalletTheme.colors.foreground60
  }
}

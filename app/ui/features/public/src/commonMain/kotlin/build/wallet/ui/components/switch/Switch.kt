package build.wallet.ui.components.switch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.compose.resId
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.WalletTheme
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.SwitchDefaults as MaterialSwitchDefaults

@Composable
fun Switch(
  model: SwitchModel,
  modifier: Modifier = Modifier,
) {
  with(model) {
    Switch(
      modifier = modifier,
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      testTag = testTag
    )
  }
}

@Composable
fun Switch(
  modifier: Modifier = Modifier,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  MaterialSwitch(
    modifier =
      modifier
        .resId(testTag),
    checked = checked,
    onCheckedChange = onCheckedChange,
    colors =
      MaterialSwitchDefaults.colors(
        checkedThumbColor = WalletTheme.colors.primaryForeground,
        uncheckedThumbColor = WalletTheme.colors.primaryForeground,
        checkedTrackColor = WalletTheme.colors.bitkeyPrimary,
        uncheckedTrackColor = WalletTheme.colors.foreground10,
        checkedBorderColor = Color.Unspecified,
        uncheckedBorderColor = Color.Unspecified
      ),
    enabled = enabled
  )
}

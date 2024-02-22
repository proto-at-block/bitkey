package build.wallet.ui.components.switch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.compose.resId
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
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
        checkedTrackColor = WalletTheme.colors.primary,
        uncheckedTrackColor = WalletTheme.colors.foreground10,
        checkedBorderColor = Color.Unspecified,
        uncheckedBorderColor = Color.Unspecified
      ),
    enabled = enabled
  )
}

@Preview
@Composable
fun SwitchPreview() {
  PreviewWalletTheme {
    Column {
      Switch(checked = true, onCheckedChange = {})
      Spacer(Modifier.height(5.dp))
      Switch(checked = false, onCheckedChange = {})
    }
  }
}

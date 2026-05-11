package build.wallet.ui.components.switch

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.LocalContainerBackgroundColor
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme

@Composable
fun Switch(
  model: SwitchModel,
  modifier: Modifier = Modifier,
) {
  with(model) {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = modifier,
      enabled = enabled,
      interactionsEnabled = interactionsEnabled,
      testTag = testTag
    )
  }
}

/**
 * Standard dimensions:
 * - Total width: 52dp
 * - Total height: 48dp (includes touch target padding)
 * - Track width: 52dp
 * - Track height: 32dp (centered in 48dp space)
 * - Track corner radius: 16dp (half of track height)
 * - Thumb diameter (unchecked): 16dp
 * - Thumb diameter (checked): 24dp (animated)
 * - Thumb padding from track edges: 4dp
 *
 * @param checked Whether the switch is checked
 * @param onCheckedChange Called when the switch is toggled
 * @param modifier Modifier for the switch
 * @param enabled Whether the switch is enabled
 * @param testTag Test tag for the switch
 * @param checkedThumbColor Color of the thumb when checked (enabled state)
 * @param uncheckedThumbColor Color of the thumb when unchecked (enabled state)
 * @param checkedTrackColor Color of the track when checked (enabled state)
 * @param uncheckedTrackColor Color of the track when unchecked (enabled state)
 * @param disabledThumbColor Color of the thumb when disabled
 * @param disabledTrackColor Color of the track when disabled
 */
@Composable
fun Switch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionsEnabled: Boolean = enabled,
  testTag: String? = null,
  checkedThumbColor: Color = WalletTheme.colors.primaryForeground,
  uncheckedThumbColor: Color = WalletTheme.colors.primaryForeground,
  checkedTrackColor: Color = WalletTheme.colors.bitkeyPrimary,
  uncheckedTrackColor: Color = WalletTheme.colors.foreground10,
  disabledThumbColor: Color = WalletTheme.colors.foreground30,
  disabledTrackColor: Color = WalletTheme.colors.foreground10,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val interopBackgroundColor = LocalContainerBackgroundColor.current ?: WalletTheme.colors.background
  val resolvedCheckedThumbColor =
    if (isDesignSystemV2Enabled && checkedThumbColor == WalletTheme.colors.primaryForeground) {
      WalletTheme.colors.background
    } else {
      checkedThumbColor
    }
  val resolvedCheckedTrackColor =
    if (isDesignSystemV2Enabled && checkedTrackColor == WalletTheme.colors.bitkeyPrimary) {
      WalletTheme.colors.inverseBackground
    } else {
      checkedTrackColor
    }

  PlatformSwitch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    enabled = enabled,
    interactionsEnabled = interactionsEnabled,
    testTag = testTag,
    checkedThumbColor = resolvedCheckedThumbColor,
    uncheckedThumbColor = uncheckedThumbColor,
    checkedTrackColor = resolvedCheckedTrackColor,
    uncheckedTrackColor = uncheckedTrackColor,
    disabledThumbColor = disabledThumbColor,
    disabledTrackColor = disabledTrackColor,
    interopBackgroundColor = interopBackgroundColor
  )
}

@Composable
internal expect fun PlatformSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  interactionsEnabled: Boolean = enabled,
  testTag: String? = null,
  checkedThumbColor: Color = WalletTheme.colors.primaryForeground,
  uncheckedThumbColor: Color = WalletTheme.colors.primaryForeground,
  checkedTrackColor: Color = WalletTheme.colors.bitkeyPrimary,
  uncheckedTrackColor: Color = WalletTheme.colors.foreground10,
  disabledThumbColor: Color = WalletTheme.colors.foreground30,
  disabledTrackColor: Color = WalletTheme.colors.foreground10,
  interopBackgroundColor: Color = WalletTheme.colors.background,
)
